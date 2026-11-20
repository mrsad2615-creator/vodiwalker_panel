#include "PersianTextBlock.h"
#include "Internationalization/Text.h"

UPersianTextBlock::UPersianTextBlock()
{
	// Default font: Vazirmatn (configured in CommonUI font family DataTable)
	// Flow direction set via Slate style — parent CommonUI theme sets EFlowDirection::RightToLeft for fa culture
}

void UPersianTextBlock::SetPersianText(const FString& LogicalText)
{
	FString Shaped = ApplyHalfSpaces(LogicalText);
	// UE's ICU + Harfbuzz (TextShaper plugin) handles bidi + shaping automatically when culture is fa
	SetText(FText::FromString(Shaped));
}

FString UPersianTextBlock::ToPersianNumerals(const FString& LatinDigits)
{
	FString Out; Out.Reserve(LatinDigits.Len());
	for(TCHAR C : LatinDigits)
	{
		if(C >= '0' && C <= '9') Out.AppendChar(TCHAR(0x06F0 + (C - '0')));
		else Out.AppendChar(C);
	}
	return Out;
}

FString UPersianTextBlock::ApplyHalfSpaces(const FString& Text)
{
	// Minimal rule: common Persian compounds — full list in DataTable DT_PersianCompounds
	FString R = Text;
	R.ReplaceInline(TEXT("می شود"), TEXT("می‌شود"));
	R.ReplaceInline(TEXT("می کرد"), TEXT("می‌کرد"));
	R.ReplaceInline(TEXT("نمی شود"), TEXT("نمی‌شود"));
	return R;
}
