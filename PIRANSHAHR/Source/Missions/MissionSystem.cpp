#include "MissionSystem.h"
void UMissionSystem::StartMission(const FString& Id) {
    for(auto& M: AvailableMissions) if(M.MissionId==Id){ ActiveMission=M; bMissionActive=true; break; }
}
void UMissionSystem::CompleteMission(bool b) { bMissionActive=false; /* reward + save */ }
void UMissionSystem::AbandonMission() { bMissionActive=false; }
float UMissionSystem::GetMissionProgress() const { return bMissionActive ? 0.5f : 0.f; /* real: distance calc */ }
