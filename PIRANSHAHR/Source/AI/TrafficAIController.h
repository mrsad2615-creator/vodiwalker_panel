// PIRANSHAHR — Traffic AI Controller (MassAI + StateTree stub)
// Real implementation: MassAI lane follow, overtake, intersection stop, pedestrian crosswalk.
// Container: Scaffolded — requires Windows UE5.4 + MassAI plugin to compile.
#pragma once
#include "CoreMinimal.h"
#include "AIController.h"
#include "TrafficAIController.generated.h"

UENUM(BlueprintType)
enum class ETrafficState : uint8 { FollowLane, StopAtIntersection, Overtake, Parked, YieldToPedestrian };

UCLASS()
class PIRANSHAHR_API ATrafficAIController : public AAIController
{
    GENERATED_BODY()
public:
    ATrafficAIController();

    // MassAI lane
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="Traffic") float DesiredSpeedKmh = 35.f;
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="Traffic") float FollowDistanceM = 8.f;
    UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="Traffic") FName CurrentRoadSpline = NAME_None;

    UFUNCTION(BlueprintCallable) void SetTrafficState(ETrafficState NewState);
    UFUNCTION(BlueprintPure) ETrafficState GetTrafficState() const { return CurrentState; }

protected:
    virtual void BeginPlay() override;
    virtual void Tick(float DeltaSeconds) override;

private:
    ETrafficState CurrentState = ETrafficState::FollowLane;
    float StateTimer = 0.f;
    void UpdateLaneFollow(float DeltaSeconds);
    void CheckIntersection();
};
