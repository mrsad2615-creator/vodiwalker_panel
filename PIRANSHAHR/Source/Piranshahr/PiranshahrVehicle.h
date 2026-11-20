// PIRANSHAHR — Chaotic Vehicles subclass (UE5.4)
// Authoritative vehicle system: Peykan hero + modular spec DataTable
#pragma once
#include "CoreMinimal.h"
#include "WheeledVehiclePawn.h"
#include "PiranshahrVehicle.generated.h"

USTRUCT(BlueprintType)
struct FVehicleSpec
{
	GENERATED_BODY()
	UPROPERTY(EditAnywhere, BlueprintReadWrite) FString DisplayNameFa;
	UPROPERTY(EditAnywhere, BlueprintReadWrite) FString Manufacturer;
	UPROPERTY(EditAnywhere, BlueprintReadWrite) float MassKg = 1200.f;
	UPROPERTY(EditAnywhere, BlueprintReadWrite) float EngineMaxTorque = 130.f; // Nm
	UPROPERTY(EditAnywhere, BlueprintReadWrite) float EngineMaxRPM = 6000.f;
	UPROPERTY(EditAnywhere, BlueprintReadWrite) float DifferentialRatio = 3.9f;
	UPROPERTY(EditAnywhere, BlueprintReadWrite) float MaxSteerAngleDeg = 38.f;
	UPROPERTY(EditAnywhere, BlueprintReadWrite) float FuelCapacityL = 50.f;
	UPROPERTY(EditAnywhere, BlueprintReadWrite) float FuelConsumptionPer100km = 9.f;
};

UCLASS(Blueprintable)
class PIRANSHAHR_API APiranshahrVehicle : public AWheeledVehiclePawn
{
	GENERATED_BODY()
public:
	APiranshahrVehicle();

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category="Piranshahr|Spec") FVehicleSpec Spec;

	// Touch input (0..1)
	UFUNCTION(BlueprintCallable) void SetThrottleInput(float Value);
	UFUNCTION(BlueprintCallable) void SetSteeringInput(float Value);
	UFUNCTION(BlueprintCallable) void SetBrakeInput(float Value);
	UFUNCTION(BlueprintCallable) void SetHandbrake(bool bOn);

	// Persian HUD helpers
	UFUNCTION(BlueprintPure) float GetSpeedKmh() const;
	UFUNCTION(BlueprintPure) float GetEngineRPM() const;
	UFUNCTION(BlueprintPure) float GetFuelPercent() const;

protected:
	virtual void Tick(float DeltaSeconds) override;
	virtual void BeginPlay() override;

private:
	float CurrentThrottle = 0.f;
	float CurrentSteer = 0.f;
	float CurrentBrake = 0.f;
	bool bHandbrake = false;
	float FuelRemainingL;
};
