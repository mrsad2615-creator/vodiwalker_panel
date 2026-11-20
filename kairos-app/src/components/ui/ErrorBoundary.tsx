import { Component, type ErrorInfo, type ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Last line of defence: React 19 unmounts the whole tree on a render error,
 * which on Android looks like the app "dying" with a black screen. This keeps a
 * calm recovery surface with a reload action instead.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Offline app: keep the trace locally so it survives a restart.
    try {
      const key = 'kairos:last-error';
      window.localStorage.setItem(
        key,
        JSON.stringify({ message: error.message, stack: error.stack, component: info.componentStack })
      );
    } catch {
      /* storage unavailable */
    }
  }

  render(): ReactNode {
    if (!this.state.error) return this.props.children;

    return (
      <div className="flex h-full min-h-[100dvh] flex-col items-center justify-center gap-4 overflow-y-auto bg-[var(--bg)] px-8 py-10 text-center">
        <span className="text-[13px] font-semibold text-[var(--text)]">مشکلی پیش آمد</span>
        <p className="text-[12px] leading-6 text-[var(--text-2)]">
          اطلاعات شما روی دستگاه محفوظ است. برنامه را دوباره باز کنید.
        </p>
        <button
          type="button"
          onClick={() => window.location.reload()}
          className="btn-royal px-5 py-2.5 text-[12px]"
        >
          بازخوانی برنامه
        </button>
      </div>
    );
  }
}
