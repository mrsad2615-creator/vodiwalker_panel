package ai.dana.data.provider

import ai.dana.data.local.DanaDatabase
import ai.dana.data.local.EngineEntity
import ai.dana.data.local.requireChange
import ai.dana.domain.ApiCredential
import ai.dana.domain.CredentialChange
import ai.dana.domain.InputIssue
import ai.dana.domain.InputRejected
import ai.dana.domain.ProviderConfig
import ai.dana.domain.ProviderNetwork
import ai.dana.domain.ProviderProfile
import ai.dana.domain.ProviderProtocol
import ai.dana.domain.ProviderRepository
import ai.dana.domain.validateFor
import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.map

internal class EngineRepository(
    private val db: DanaDatabase,
    private val vault: KeystoreVault,
) : ProviderRepository {
    private val dao = db.engines()

    override val profiles = dao.observe().map { engines ->
        engines.map {
            ProviderProfile(it.id, config(it.name, it.protocol, it.network, it.baseUrl, it.model), it.hasCredential)
        }
    }

    override suspend fun save(id: String?, config: ProviderConfig, key: CredentialChange): String = db.withTransaction {
        val current = id?.let { requireProfile(it) }
        val profileId = id ?: UUID.randomUUID().toString()
        key.validateFor(current?.profile(), config)
        val sealed = when (key) {
            CredentialChange.Keep -> current?.sealedKey
            CredentialChange.Remove -> null
            is CredentialChange.Replace -> vault.seal(profileId, config.credentialScope, key.credential)
        }
        dao.upsert(
            EngineEntity(
                id = profileId,
                name = config.name,
                protocol = config.protocol.name,
                network = config.network.name,
                baseUrl = config.baseUrl,
                model = config.model,
                sealedKey = sealed,
            ),
        )
        profileId
    }

    override suspend fun delete(id: String) = requireChange(dao.delete(id))

    override suspend fun credentialFor(profile: ProviderProfile): ApiCredential? {
        val current = requireProfile(profile.id)
        if (profile != current.profile()) throw InputRejected(InputIssue.RECORD_NOT_FOUND)
        return current.sealedKey?.let { vault.open(current.id, profile.config.credentialScope, it) }
    }

    private suspend fun requireProfile(id: String): EngineEntity = dao.find(id)
        ?: throw InputRejected(InputIssue.RECORD_NOT_FOUND)

    private fun EngineEntity.profile() = ProviderProfile(
        id, config(name, protocol, network, baseUrl, model), sealedKey != null,
    )

    private fun config(name: String, protocol: String, network: String, url: String, model: String) =
        ProviderConfig.create(name, ProviderProtocol.valueOf(protocol), ProviderNetwork.valueOf(network), url, model)
}
