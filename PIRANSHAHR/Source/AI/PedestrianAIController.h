// PIRANSHAHR — Pedestrian AI (sidewalk + crosswalk)
#pragma once
#include "CoreMinimal.h"
#include "AIController.h"
#include "PedestrianAIController.generated.h"

UCLASS()
class PIRANSHAHR_API APedestrianAIController : public AAIController
{
    GENERATED_BODY()
public:
    APedestrianAIController();
    UPROPERTY(EditAnywhere) float WalkSpeedMps = 1.2f;
    UPROPERTY(EditAnywhere) FName SidewalkSpline;
protected:
    virtual void Tick(float DeltaSeconds) override;
};
