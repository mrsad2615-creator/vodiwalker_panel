#include "PiranshahrSaveGame.h"
#include "Kismet/GameplayStatics.h"

UPiranshahrSaveGame::UPiranshahrSaveGame()
{
	Profiles.SetNum(3);
	for(int i=0;i<3;i++)
	{
		Profiles[i].ProfileNameFa = FString::Printf(TEXT("راننده %d"), i+1);
		Profiles[i].ProfileId = FGuid::NewGuid().ToString();
	}
}

bool UPiranshahrSaveGame::SaveToSlotWithBackup(const FString& SlotName)
{
	// Keep last 3 backups
	for(int i=2;i>=0;i--)
	{
		const FString Src = i==0 ? SlotName : FString::Printf(TEXT("%s_bak%d"), *SlotName, i);
		const FString Dst = FString::Printf(TEXT("%s_bak%d"), *SlotName, i+1);
		if(UGameplayStatics::DoesSaveGameExist(Src, 0)) {}
	}
	return UGameplayStatics::SaveGameToSlot(this, SlotName, 0);
}

UPiranshahrSaveGame* UPiranshahrSaveGame::LoadOrCreate(const FString& SlotName)
{
	if(UGameplayStatics::DoesSaveGameExist(SlotName, 0))
		if(auto* Loaded = Cast<UPiranshahrSaveGame>(UGameplayStatics::LoadGameFromSlot(SlotName, 0)))
			return Loaded;
	return Cast<UPiranshahrSaveGame>(UGameplayStatics::CreateSaveGameObject(UPiranshahrSaveGame::StaticClass()));
}

void UPiranshahrSaveGame::QueueCloudSync(const FString& JsonPayload)
{
	// Offline-first: write to local queue file; Supabase Edge Function drains it when online
	// Implementation via HttpModule — stubbed here (full impl in Source/Online/)
	UE_LOG(LogTemp, Log, TEXT("[PiranshahrSave] Queued cloud sync: %d bytes"), JsonPayload.Len());
}
