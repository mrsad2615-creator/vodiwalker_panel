#include "WeatherSubsystem.h"
void UWeatherSubsystem::SetWeather(EWeatherType W, float T) { CurrentWeather=W; /* lerp Niagara + postprocess */ }
void UWeatherSubsystem::SetSeason(ESeason S) { CurrentSeason=S; }
void UWeatherSubsystem::SetTimeOfDay(float H) { TimeOfDayHours = FMath::Clamp(H,0.f,24.f); }
FString UWeatherSubsystem::GetWeatherFa() const {
    switch(CurrentWeather){case EWeatherType::Rain: return TEXT("بارانی"); case EWeatherType::Snow: return TEXT("برفی"); case EWeatherType::Fog: return TEXT("مه‌آلود"); case EWeatherType::DustStorm: return TEXT("غبارآلود"); default: return TEXT("صاف");}
}
FString UWeatherSubsystem::GetSeasonFa() const {
    switch(CurrentSeason){case ESeason::Spring: return TEXT("بهار"); case ESeason::Summer: return TEXT("تابستان"); case ESeason::Autumn: return TEXT("پاییز"); default: return TEXT("زمستان");}
}
