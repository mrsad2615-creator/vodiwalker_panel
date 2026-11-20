// PIRANSHAHR — Weather/Seasons/Time subsystem (Niagara + material)
// Independent: weather ≠ season; time is selectable (not realtime).
#pragma once
#include "CoreMinimal.h"
#include "Subsystems/GameInstanceSubsystem.h"
#include "WeatherSubsystem.generated.h"

UENUM(BlueprintType)
enum class EWeatherType : uint8 { Clear, Rain, Snow, Fog, DustStorm };
UENUM(BlueprintType)
enum class ESeason : uint8 { Spring, Summer, Autumn, Winter };

UCLASS()
class PIRANSHAHR_API UWeatherSubsystem : public UGameInstanceSubsystem
{
    GENERATED_BODY()
public:
    // Current — Persian UI shows these
    UPROPERTY(EditAnywhere, BlueprintReadWrite) EWeatherType CurrentWeather = EWeatherType::Clear;
    UPROPERTY(EditAnywhere, BlueprintReadWrite) ESeason CurrentSeason = ESeason::Spring;
    UPROPERTY(EditAnywhere, BlueprintReadWrite, meta=(ClampMin="0", ClampMax="24")) float TimeOfDayHours = 14.f; // 14:00 default

    UFUNCTION(BlueprintCallable) void SetWeather(EWeatherType W, float TransitionSec=2.f);
    UFUNCTION(BlueprintCallable) void SetSeason(ESeason S);
    UFUNCTION(BlueprintCallable) void SetTimeOfDay(float Hours);
    UFUNCTION(BlueprintPure) FString GetWeatherFa() const;
    UFUNCTION(BlueprintPure) FString GetSeasonFa() const;

    // POCO: disable volumetric fog, use material fog; Niagara rain limited to 500 particles
    UPROPERTY(EditAnywhere) bool bVolumetricFogEnabled = false;
    UPROPERTY(EditAnywhere) int32 MaxRainParticles = 500;
};
