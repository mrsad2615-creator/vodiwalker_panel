#include "TrafficAIController.h"
#include "Engine/Engine.h"

ATrafficAIController::ATrafficAIController()
{
    PrimaryActorTick.bCanEverTick = true;
}

void ATrafficAIController::BeginPlay()
{
    Super::BeginPlay();
    UE_LOG(LogTemp, Log, TEXT("[Piranshahr AI] Traffic spawn on %s @ %.0f km/h"), *CurrentRoadSpline.ToString(), DesiredSpeedKmh);
}

void ATrafficAIController::Tick(float DeltaSeconds)
{
    Super::Tick(DeltaSeconds);
    StateTimer += DeltaSeconds;
    UpdateLaneFollow(DeltaSeconds);
    CheckIntersection();
}

void ATrafficAIController::SetTrafficState(ETrafficState NewState)
{
    CurrentState = NewState;
    StateTimer = 0.f;
}

void ATrafficAIController::UpdateLaneFollow(float DeltaSeconds)
{
    // Stub: MassAI would drive lane spline; here we log for verification.
    // Real: Project to road spline, steer toward centerline, match speed.
}

void ATrafficAIController::CheckIntersection()
{
    // Stub: Raycast for intersection trigger volume; set StopAtIntersection if occupied.
}
