#pragma once
#include "CoreMinimal.h"
#include "Components/TextBlock.h"
#include "PersianTextBlock.generated.h"

/**
 * Vazirmatn RTL TextBlock — handles:
 * - RTL paragraph direction
 * - Persian shaping (harfbuzz via FreeType; UE TextShaper plugin)
 * - U+200C half-space
 * - Persian numerals (۰-۹)
 * - Mixed LTR numerals inside RTL
 */
UCLASS()
class PIRANSHAHR_API UPersianTextBlock : public UTextBlock
{
	GENERATED_BODY()
public:
	UPersianTextBlock();

	// Set raw Persian (logical order); widget handles visual reordering
	UFUNCTION(BlueprintCallable) void SetPersianText(const FString& LogicalText);

	// Helpers
	static FString ToPersianNumerals(const FString& LatinDigits);
	static FString ApplyHalfSpaces(const FString& Text); // e.g. "می شود" → "می‌شود"
};
