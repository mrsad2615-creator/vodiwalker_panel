// PIRANSHAHR — Mission system (taxi, delivery, free-roam)
#pragma once
#include "CoreMinimal.h"
#include "Subsystems/GameInstanceSubsystem.h"
#include "MissionSystem.generated.h"

UENUM(BlueprintType)
enum class EMissionType : uint8 { Taxi, Delivery, FreeRoam, RepairShop };

USTRUCT(BlueprintType)
struct FMissionDef
{
    GENERATED_BODY()
    UPROPERTY(EditAnywhere) FString MissionId;
    UPROPERTY(EditAnywhere) FString TitleFa;
    UPROPERTY(EditAnywhere) FString DescriptionFa;
    UPROPERTY(EditAnywhere) EMissionType Type = EMissionType::Taxi;
    UPROPERTY(EditAnywhere) FVector StartLocation = FVector::ZeroVector;
    UPROPERTY(EditAnywhere) FVector EndLocation = FVector::ZeroVector;
    UPROPERTY(EditAnywhere) float RewardToman = 50000.f;
    UPROPERTY(EditAnywhere) float TimeLimitSec = 180.f;
};

UCLASS()
class PIRANSHAHR_API UMissionSystem : public UGameInstanceSubsystem
{
    GENERATED_BODY()
public:
    UPROPERTY(EditAnywhere, BlueprintReadWrite) TArray<FMissionDef> AvailableMissions;
    UPROPERTY(BlueprintReadOnly) FMissionDef ActiveMission;
    UPROPERTY(BlueprintReadOnly) bool bMissionActive = false;

    UFUNCTION(BlueprintCallable) void StartMission(const FString& MissionId);
    UFUNCTION(BlueprintCallable) void CompleteMission(bool bSuccess);
    UFUNCTION(BlueprintCallable) void AbandonMission();
    UFUNCTION(BlueprintPure) float GetMissionProgress() const; // 0..1 distance to target
};
