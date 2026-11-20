/** Single source of truth for the build identity shown inside the app. */
export const APP_VERSION: string = typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : '0.0.0-dev';
export const BUILD_NUMBER: string = typeof __BUILD_NUMBER__ === 'string' ? __BUILD_NUMBER__ : 'dev';
export const BUILD_SHA: string = typeof __BUILD_SHA__ === 'string' ? __BUILD_SHA__ : 'local';
export const BUILD_TIME: string = typeof __BUILD_TIME__ === 'string' ? __BUILD_TIME__ : '';

/** e.g. `v1.3.0 · build 8 · c845560` */
export const VERSION_LABEL = `v${APP_VERSION} · build ${BUILD_NUMBER} · ${BUILD_SHA}`;
