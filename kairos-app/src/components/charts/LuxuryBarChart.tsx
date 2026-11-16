import { useState } from 'react';
import { Numeric } from '@/components/ui/Numeric';

export interface BarChartDatum {
  key: string;
  label: string;
  value: number;
  sublabel?: string;
  highlighted?: boolean;
}

interface LuxuryBarChartProps {
  data: BarChartDatum[];
  maxValue?: number;
  unitSuffix?: string;
  height?: number;
}

export function LuxuryBarChart({
  data,
  maxValue,
  unitSuffix = '',
  height = 148,
}: LuxuryBarChartProps) {
  const [activeKey, setActiveKey] = useState<string | null>(null);
  const computedMax = Math.max(
    1,
    maxValue ?? Math.max(...data.map((d) => d.value), 1)
  );
  const activeItem = data.find((d) => d.key === activeKey);

  return (
    <div className="space-y-2">
      {/* Subtle Selection Header */}
      <div className="flex h-5 items-center justify-between text-[11px]">
        {activeItem ? (
          <>
            <span className="text-[#A79EB9]">{activeItem.sublabel || activeItem.label}</span>
            <Numeric
              value={activeItem.value}
              suffix={unitSuffix}
              variant="geo"
              className="font-semibold text-[#B69AEF]"
            />
          </>
        ) : (
          <span className="text-[10.5px] text-[#6E6680]">برای مشاهده جزئیات روی ستون لمس کنید</span>
        )}
      </div>

      {/* Bars */}
      <div
        className="flex items-end justify-between gap-1.5"
        style={{ height }}
      >
        {data.map((item) => {
          const pct = Math.max(0, Math.min(100, (item.value / computedMax) * 100));
          const isSelected = activeKey === item.key;
          const isPeak = item.highlighted || isSelected;

          return (
            <button
              key={item.key}
              type="button"
              onClick={() => setActiveKey(isSelected ? null : item.key)}
              className="group flex h-full flex-1 flex-col items-center justify-end gap-1.5 focus:outline-none"
            >
              <div className="relative flex w-full flex-1 items-end justify-center rounded-lg bg-[#100F16]/80 px-[2px] pt-2">
                <div
                  className="w-full max-w-[26px] rounded-t-[7px] rounded-b-[3px] transition-all duration-300"
                  style={{
                    height: item.value > 0 ? `${Math.max(8, pct)}%` : '3px',
                    background:
                      item.value === 0
                        ? 'rgba(110, 102, 128, 0.25)'
                        : isPeak
                        ? 'linear-gradient(180deg, #B69AEF 0%, #8052D6 100%)'
                        : 'linear-gradient(180deg, rgba(128, 82, 214, 0.85) 0%, rgba(36, 25, 54, 0.95) 100%)',
                    boxShadow: isPeak && item.value > 0 ? '0 0 14px rgba(128, 82, 214, 0.45)' : undefined,
                  }}
                />
              </div>
              <span
                className={`truncate text-[9.5px] transition ${
                  isSelected ? 'font-semibold text-[#F4F1F8]' : 'text-[#6E6680] group-hover:text-[#A79EB9]'
                }`}
              >
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
