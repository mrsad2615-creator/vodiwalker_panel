#include "InteriorComponent.h"
#include "Engine/Engine.h"
UInteriorComponent::UInteriorComponent() { PrimaryComponentTick.bCanEverTick = false; }
void UInteriorComponent::EnterInterior(APawn* Player) { UE_LOG(LogTemp, Log, TEXT("[Interior] Enter %s"), *BuildingNameFa); /* stream interior level */ }
void UInteriorComponent::ExitInterior(APawn* Player) { UE_LOG(LogTemp, Log, TEXT("[Interior] Exit %s"), *BuildingNameFa); }
