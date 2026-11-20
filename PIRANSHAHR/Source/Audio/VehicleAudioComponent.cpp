#include "VehicleAudioComponent.h"
UVehicleAudioComponent::UVehicleAudioComponent() { PrimaryComponentTick.bCanEverTick = false; }
void UVehicleAudioComponent::BeginPlay() { Super::BeginPlay(); }
void UVehicleAudioComponent::SetEngineRPM(float RPM) { /* set MetaSound param EngineRPM */ }
void UVehicleAudioComponent::PlayHorn() { /* trigger MetaSound horn */ }
void UVehicleAudioComponent::SetTireSlip(float Slip) { /* tire squeal */ }
