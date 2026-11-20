#include "PiranshahrVehicle.h"
#include "ChaosWheeledVehicleMovementComponent.h"
#include "Engine/Engine.h"

APiranshahrVehicle::APiranshahrVehicle()
{
	PrimaryActorTick.bCanEverTick = true;
	FuelRemainingL = 50.f;
	// Chaos movement component configured in BP; here we set defaults for POCO perf
	if (auto* Move = Cast<UChaosWheeledVehicleMovementComponent>(GetVehicleMovement()))
	{
		Move->EngineSetup.MaxTorque = 130.f;
		Move->EngineSetup.MaxRPM = 6000.f;
		Move->EngineSetup.TorqueCurve.GetRichCurve()->Reset();
		// Simplified Peykan torque curve (Nm vs RPM) — real curve tuned via DT
		Move->EngineSetup.TorqueCurve.GetRichCurve()->AddKey(800.f, 60.f);
		Move->EngineSetup.TorqueCurve.GetRichCurve()->AddKey(1800.f, 105.f);
		Move->EngineSetup.TorqueCurve.GetRichCurve()->AddKey(3000.f, 130.f);
		Move->EngineSetup.TorqueCurve.GetRichCurve()->AddKey(4500.f, 120.f);
		Move->EngineSetup.TorqueCurve.GetRichCurve()->AddKey(6000.f, 80.f);
		Move->DifferentialSetup.DifferentialType = EVehicleDifferential::LimitedSlip_FrontDrive;
		Move->DifferentialSetup.FrontRearSplit = 0.5f;
		// Suspension tuned for city roads (not offroad)
		Move->bLegacySuspension = false;
	}
}

void APiranshahrVehicle::BeginPlay()
{
	Super::BeginPlay();
	FuelRemainingL = Spec.FuelCapacityL;
	UE_LOG(LogTemp, Log, TEXT("[Piranshahr] Vehicle spawned: %s (%.0f kg)"), *Spec.DisplayNameFa, Spec.MassKg);
}

void APiranshahrVehicle::Tick(float DeltaSeconds)
{
	Super::Tick(DeltaSeconds);
	// Fuel consumption (simplified, tied to throttle * speed)
	const float SpeedKmh = GetSpeedKmh();
	const float ConsumptionPerSec = (Spec.FuelConsumptionPer100km / 100.f) * (FMath::Abs(CurrentThrottle) * SpeedKmh / 3600.f);
	FuelRemainingL = FMath::Max(0.f, FuelRemainingL - ConsumptionPerSec * DeltaSeconds);
}

void APiranshahrVehicle::SetThrottleInput(float Value) { CurrentThrottle = FMath::Clamp(Value, -1.f, 1.f); if(auto* M=Cast<UChaosWheeledVehicleMovementComponent>(GetVehicleMovement())) M->SetThrottleInput(CurrentThrottle); }
void APiranshahrVehicle::SetSteeringInput(float Value) { CurrentSteer = FMath::Clamp(Value, -1.f, 1.f); if(auto* M=Cast<UChaosWheeledVehicleMovementComponent>(GetVehicleMovement())) M->SetSteeringInput(CurrentSteer); }
void APiranshahrVehicle::SetBrakeInput(float Value)    { CurrentBrake = FMath::Clamp(Value, 0.f, 1.f); if(auto* M=Cast<UChaosWheeledVehicleMovementComponent>(GetVehicleMovement())) M->SetBrakeInput(CurrentBrake); }
void APiranshahrVehicle::SetHandbrake(bool bOn)         { bHandbrake=bOn; if(auto* M=Cast<UChaosWheeledVehicleMovementComponent>(GetVehicleMovement())) M->SetHandbrakeInput(bOn); }

float APiranshahrVehicle::GetSpeedKmh() const
{
	if(auto* M=Cast<UChaosWheeledVehicleMovementComponent>(GetVehicleMovement())) return M->GetForwardSpeed() * 3.6f;
	return GetVelocity().Size() * 0.036f;
}
float APiranshahrVehicle::GetEngineRPM() const
{
	if(auto* M=Cast<UChaosWheeledVehicleMovementComponent>(GetVehicleMovement())) return M->GetEngineRotationSpeed();
	return 800.f;
}
float APiranshahrVehicle::GetFuelPercent() const { return Spec.FuelCapacityL > 0 ? FuelRemainingL / Spec.FuelCapacityL : 1.f; }
