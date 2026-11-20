#pragma once
#include "CoreMinimal.h"
#include "GameFramework/SaveGame.h"
#include "PiranshahrSaveGame.generated.h"

USTRUCT(BlueprintType)
struct FPiranshahrProfile
{
	GENERATED_BODY()
	UPROPERTY() FString ProfileNameFa = TEXT("راننده ۱");
	UPROPERTY() FString ProfileId;
	UPROPERTY() float PlaytimeSeconds = 0.f;
	UPROPERTY() int32 Level = 1;
	UPROPERTY() TMap<FString,int32> OwnedVehicles;
	UPROPERTY() FString LastVehicleRow = TEXT("Peykan");
	UPROPERTY() FVector LastLocation = FVector::ZeroVector;
};

UCLASS()
class PIRANSHAHR_API UPiranshahrSaveGame : public USaveGame
{
	GENERATED_BODY()
public:
	UPiranshahrSaveGame();

	UPROPERTY(BlueprintReadWrite) TArray<FPiranshahrProfile> Profiles;
	UPROPERTY(BlueprintReadWrite) int32 ActiveProfileIndex = 0;
	UPROPERTY(BlueprintReadWrite) FString GraphicsPreset = TEXT("Balanced"); // Performance/Balanced/Ultra
	UPROPERTY(BlueprintReadWrite) float MasterVolume = 1.f;
	UPROPERTY(BlueprintReadWrite) FString Language = TEXT("fa"); // fa default

	// Auto-save (offline, every 30s + on milestone)
	UFUNCTION(BlueprintCallable) bool SaveToSlotWithBackup(const FString& SlotName);
	UFUNCTION(BlueprintCallable) static UPiranshahrSaveGame* LoadOrCreate(const FString& SlotName);

	// Supabase optional sync (offline-first: queue when offline)
	UFUNCTION(BlueprintCallable) void QueueCloudSync(const FString& JsonPayload);
};
