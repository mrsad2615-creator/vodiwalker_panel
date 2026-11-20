// PIRANSHAHR — Per-vehicle MetaSounds audio (engine, tire, horn)
#pragma once
#include "CoreMinimal.h"
#include "Components/ActorComponent.h"
#include "VehicleAudioComponent.generated.h"

UCLASS(ClassGroup=(Piranshahr), meta=(BlueprintSpawnableComponent))
class PIRANSHAHR_API UVehicleAudioComponent : public UActorComponent
{
    GENERATED_BODY()
public:
    UVehicleAudioComponent();
    UPROPERTY(EditAnywhere) TObjectPtr<class UMetaSoundSource> EngineMetaSound;
    UPROPERTY(EditAnywhere) float EngineVolume = 1.f;
    UFUNCTION(BlueprintCallable) void SetEngineRPM(float RPM);
    UFUNCTION(BlueprintCallable) void PlayHorn();
    UFUNCTION(BlueprintCallable) void SetTireSlip(float Slip);
protected: virtual void BeginPlay() override;
};
