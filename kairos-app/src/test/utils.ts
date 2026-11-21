
/**
 * Cold CI runners are several times slower than a dev machine: the first Dexie
 * open, the font settle and the shell mount can all exceed Testing Library's
 * 1s default. Booting is not what these tests are measuring, so they wait long
 * enough to never fail for being early.
 */
export const BOOT_TIMEOUT = 8000;
