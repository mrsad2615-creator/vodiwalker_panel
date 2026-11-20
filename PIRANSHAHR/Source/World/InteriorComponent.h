// PIRANSHAHR — Enterable interior marker (5 hero buildings)
#pragma once
#include "CoreMinimal.h"
#include "Components/ActorComponent.h"
#include "InteriorComponent.generated.h"

UCLASS(ClassGroup=(Piranshahr), meta=(BlueprintSpawnableComponent))
class PIRANSHAHR_API UInteriorComponent : public UActorComponent
{
    GENERATED_BODY()
public:
    UInteriorComponent();
    UPROPERTY(EditAnywhere, BlueprintReadWrite) FString BuildingNameFa; // e.g. "خانه بازیکن"
    UPROPERTY(EditAnywhere, BlueprintReadWrite) bool bEnterable = false;
    UPROPERTY(EditAnywhere, BlueprintReadWrite) int32 InteriorLevelStreamId = -1; // World Partition cell
    UFUNCTION(BlueprintCallable) void EnterInterior(APawn* Player);
    UFUNCTION(BlueprintCallable) void ExitInterior(APawn* Player);
};
