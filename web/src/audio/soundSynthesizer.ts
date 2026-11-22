import { SoundEffect } from './soundEffect';

const SAMPLE_RATE = 44100;
const STAGGER_MS = 115;

function sine(phase: number): number {
  return Math.sin(2 * Math.PI * phase);
}

function triangle(phase: number): number {
  const frac = phase - Math.floor(phase);
  return 4 * Math.abs(frac - 0.5) - 1;
}

function clamp(v: number): number {
  return Math.max(-0.95, Math.min(0.95, v));
}

function createBuffer(durationSec: number, generator: (t: number, i: number) => number): Float32Array {
  const length = Math.max(1, Math.floor(SAMPLE_RATE * durationSec));
  const data = new Float32Array(length);
  for (let i = 0; i < length; i++) {
    data[i] = clamp(generator(i / SAMPLE_RATE, i));
  }
  return data;
}

function expEnv(t: number, attack: number, decay: number): number {
  const a = attack > 0 ? Math.min(1, t / attack) : 1;
  return a * Math.exp(-t / Math.max(0.001, decay));
}

function synthesizeSamples(effect: SoundEffect): Float32Array {
  let seed = 0x1234567;
  const noise = () => {
    seed = (seed * 1664525 + 1013904223) >>> 0;
    return (seed / 0xffffffff) * 2 - 1;
  };

  switch (effect) {
    case 'PIECE_SELECT':
      return createBuffer(0.055, (t) => {
        const env = expEnv(t, 0.002, 0.014);
        return env * (0.35 * sine(520 * t) + 0.15 * sine(1040 * t));
      });

    case 'MOVE':
      return createBuffer(0.11, (t) => {
        const thud = expEnv(t, 0.002, 0.022) * sine((240 - 110 * t * 6) * t);
        const knock = expEnv(t, 0.001, 0.01) * (0.4 * sine(680 * t) + 0.25 * noise());
        return 0.65 * thud + 0.45 * knock;
      });

    case 'CAPTURE':
      return createBuffer(0.19, (t) => {
        const body = expEnv(t, 0.002, 0.045) * sine((185 - 70 * t * 3) * t);
        const snap = expEnv(t, 0.001, 0.018) * (0.55 * noise() + 0.4 * sine(880 * t));
        const resonance = expEnv(t, 0.004, 0.065) * 0.25 * sine(340 * t);
        return 0.72 * body + 0.55 * snap + resonance;
      });

    case 'CASTLE':
      return createBuffer(0.24, (t) => {
        const first =
          t < 0.1
            ? expEnv(t, 0.002, 0.024) * (0.65 * sine(230 * t) + 0.25 * sine(620 * t))
            : 0;
        const t2 = t - 0.095;
        const second =
          t2 >= 0
            ? expEnv(t2, 0.002, 0.03) * (0.72 * sine(275 * t2) + 0.3 * sine(740 * t2))
            : 0;
        return first + second;
      });

    case 'CARD_DRAW_NUMBER':
      return createBuffer(0.16, (t) => {
        const swish = expEnv(t, 0.008, 0.038) * 0.32 * noise();
        const flick = expEnv(Math.max(0, t - 0.03), 0.002, 0.035) * 0.42 * sine((460 + 260 * t) * t);
        return swish + (t >= 0.03 ? flick : 0);
      });

    case 'CARD_DRAW_SPECIAL':
      return createBuffer(0.32, (t) => {
        const swish = expEnv(t, 0.008, 0.04) * 0.28 * noise();
        const n1 = expEnv(t, 0.004, 0.09) * 0.36 * triangle(523.25 * t);
        const t2 = t - 0.075;
        const n2 = t2 >= 0 ? expEnv(t2, 0.004, 0.12) * 0.42 * triangle(659.25 * t2) : 0;
        const t3 = t - 0.145;
        const n3 = t3 >= 0 ? expEnv(t3, 0.004, 0.13) * 0.38 * sine(783.99 * t3) : 0;
        return swish + n1 + n2 + n3;
      });

    case 'CHECK':
      return createBuffer(0.38, (t) => {
        const n1 = expEnv(t, 0.003, 0.11) * (0.48 * sine(659.25 * t) + 0.2 * sine(1318.5 * t));
        const t2 = t - 0.11;
        const n2 =
          t2 >= 0
            ? expEnv(t2, 0.003, 0.18) * (0.52 * sine(880 * t2) + 0.22 * sine(1760 * t2))
            : 0;
        return n1 + n2;
      });

    case 'VICTORY': {
      const notes = [523.25, 659.25, 783.99, 1046.5];
      return createBuffer(0.85, (t) => {
        let sum = 0;
        for (let idx = 0; idx < notes.length; idx++) {
          const dt = t - idx * 0.105;
          if (dt >= 0) {
            const env = expEnv(dt, 0.006, idx === 3 ? 0.42 : 0.18);
            sum += env * (0.32 * sine(notes[idx] * dt) + 0.14 * triangle(notes[idx] * 2 * dt));
          }
        }
        return sum;
      });
    }

    case 'DEFEAT': {
      const notes = [440, 392, 349.23, 293.66];
      return createBuffer(0.75, (t) => {
        let sum = 0;
        for (let idx = 0; idx < notes.length; idx++) {
          const dt = t - idx * 0.13;
          if (dt >= 0) {
            const env = expEnv(dt, 0.008, idx === 3 ? 0.35 : 0.16);
            sum += env * (0.34 * triangle(notes[idx] * dt) + 0.12 * sine(notes[idx] * 0.5 * dt));
          }
        }
        return sum;
      });
    }

    case 'DRAW_STALEMATE':
      return createBuffer(0.55, (t) => {
        const n1 = expEnv(t, 0.008, 0.18) * 0.38 * sine(440 * t);
        const t2 = t - 0.16;
        const n2 = t2 >= 0 ? expEnv(t2, 0.008, 0.25) * 0.38 * sine(440 * t2) : 0;
        return n1 + n2;
      });

    case 'REVIVE':
      return createBuffer(0.45, (t) => {
        const freq = 340 + 520 * Math.min(1, t / 0.35);
        const env = expEnv(t, 0.015, 0.22);
        return env * (0.4 * sine(freq * t) + 0.22 * sine(freq * 1.5 * t));
      });

    case 'FREEZE':
      return createBuffer(0.42, (t) => {
        const shimmer =
          expEnv(t, 0.005, 0.19) *
          (0.32 * sine(1568 * t) + 0.24 * sine(2093 * t) + 0.18 * sine(2637 * t));
        const frost = expEnv(t, 0.003, 0.06) * 0.22 * noise();
        return shimmer + frost;
      });

    case 'SHIELD':
      return createBuffer(0.38, (t) => {
        const clang =
          expEnv(t, 0.002, 0.18) *
          (0.38 * sine(587.33 * t) + 0.26 * sine(880 * t) + 0.16 * sine(1480 * t));
        const low = expEnv(t, 0.004, 0.12) * 0.28 * sine(293.66 * t);
        return clang + low;
      });

    case 'MERGE':
      return createBuffer(0.42, (t) => {
        const f1 = 280 + 360 * Math.min(1, t / 0.28);
        const f2 = 420 + 220 * Math.min(1, t / 0.28);
        const env = expEnv(t, 0.012, 0.22);
        return env * (0.35 * sine(f1 * t) + 0.3 * triangle(f2 * t));
      });

    case 'SACRIFICE':
      return createBuffer(0.36, (t) => {
        const crack = expEnv(t, 0.002, 0.04) * 0.42 * noise();
        const drop = expEnv(t, 0.005, 0.18) * 0.45 * sine((520 - 290 * t) * t);
        return crack + drop;
      });

    case 'SWAP':
      return createBuffer(0.28, (t) => {
        const up = expEnv(t, 0.005, 0.1) * 0.35 * sine((360 + 500 * t) * t);
        const t2 = t - 0.09;
        const down = t2 >= 0 ? expEnv(t2, 0.005, 0.11) * 0.35 * sine((740 - 420 * t2) * t2) : 0;
        return up + down;
      });

    case 'CLONE':
      return createBuffer(0.32, (t) => {
        const p1 = expEnv(t, 0.004, 0.09) * 0.38 * sine(587.33 * t);
        const t2 = t - 0.1;
        const p2 = t2 >= 0 ? expEnv(t2, 0.004, 0.13) * 0.4 * sine(880 * t2) : 0;
        return p1 + p2;
      });

    case 'HIJACK':
      return createBuffer(0.32, (t) => {
        const buzz = expEnv(t, 0.004, 0.14) * 0.38 * triangle((660 - 240 * t) * t);
        const zap = expEnv(t, 0.002, 0.045) * 0.25 * noise();
        return buzz + zap;
      });

    case 'PROMOTE': {
      const notes = [440, 554.37, 659.25, 880];
      return createBuffer(0.46, (t) => {
        let sum = 0;
        for (let i = 0; i < notes.length; i++) {
          const dt = t - i * 0.065;
          if (dt >= 0) {
            sum += expEnv(dt, 0.004, 0.16) * 0.3 * sine(notes[i] * dt);
          }
        }
        return sum;
      });
    }

    case 'SUICIDE':
      return createBuffer(0.36, (t) => {
        const drop = expEnv(t, 0.004, 0.2) * 0.5 * triangle((320 - 210 * t) * t);
        const grit = expEnv(t, 0.002, 0.05) * 0.28 * noise();
        return drop + grit;
      });

    case 'WILD': {
      const notes = [523.25, 659.25, 783.99, 987.77, 1318.5];
      return createBuffer(0.54, (t) => {
        let sum = 0;
        for (let i = 0; i < notes.length; i++) {
          const dt = t - i * 0.055;
          if (dt >= 0) {
            sum += expEnv(dt, 0.004, 0.2) * 0.26 * sine(notes[i] * dt);
          }
        }
        return sum;
      });
    }

    case 'SNIPER':
      return createBuffer(0.24, (t) => {
        const crack = expEnv(t, 0.001, 0.022) * 0.7 * noise();
        const laser = expEnv(t, 0.001, 0.12) * 0.45 * sine((1720 - 900 * t) * t);
        return crack + laser;
      });

    case 'PORTAL':
      return createBuffer(0.38, (t) => {
        const sweep = expEnv(t, 0.02, 0.22) * 0.55 * sine((280 + 720 * t) * t);
        const shimmer = expEnv(t, 0.04, 0.16) * 0.28 * triangle((560 + 1440 * t) * t);
        return sweep + shimmer;
      });

    case 'TRAP_ARM':
      return createBuffer(0.16, (t) => {
        const click1 = expEnv(t, 0.001, 0.014) * (0.45 * noise() + 0.25 * sine(1440 * t));
        const dt2 = Math.max(0, t - 0.048);
        const click2 = expEnv(dt2, 0.001, 0.018) * (0.5 * noise() + 0.3 * sine(1880 * dt2));
        return click1 + click2;
      });

    case 'TRAP_TRIGGER':
      return createBuffer(0.48, (t) => {
        const blast = expEnv(t, 0.002, 0.14) * 0.75 * noise();
        const rumble = expEnv(t, 0.006, 0.32) * 0.65 * sine((110 - 45 * t) * t);
        return blast + rumble;
      });

    case 'DRAW_TWO':
      return createBuffer(0.26, (t) => {
        const swish1 = expEnv(t, 0.002, 0.045) * (0.35 * noise() + 0.2 * sine(540 * t));
        const dt2 = Math.max(0, t - 0.065);
        const swish2 = expEnv(dt2, 0.002, 0.055) * (0.45 * noise() + 0.25 * sine(720 * dt2));
        return swish1 + swish2;
      });

    case 'ALERT':
      return createBuffer(0.18, (t) => {
        const env = expEnv(t, 0.003, 0.075);
        return env * (0.36 * triangle(320 * t) + 0.2 * sine(240 * t));
      });
  }
}

class WebSoundManager {
  private ctx: AudioContext | null = null;
  private cache = new Map<SoundEffect, AudioBuffer>();
  private muted = false;

  setMuted(muted: boolean): void {
    this.muted = muted;
  }

  private ensureContext(): AudioContext | null {
    if (typeof window === 'undefined') return null;
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (!AudioCtx) return null;
      this.ctx = new AudioCtx();
    }
    if (this.ctx.state === 'suspended') {
      void this.ctx.resume().catch(() => undefined);
    }
    return this.ctx;
  }

  private getBuffer(ctx: AudioContext, effect: SoundEffect): AudioBuffer {
    const existing = this.cache.get(effect);
    if (existing) return existing;

    const samples = synthesizeSamples(effect);
    const buffer = ctx.createBuffer(1, samples.length, SAMPLE_RATE);
    buffer.getChannelData(0).set(samples);
    this.cache.set(effect, buffer);
    return buffer;
  }

  play(effect: SoundEffect): void {
    if (this.muted) return;
    const ctx = this.ensureContext();
    if (!ctx) return;

    const buffer = this.getBuffer(ctx, effect);
    const src = ctx.createBufferSource();
    src.buffer = buffer;
    // Match Android ±3% pitch jitter for tactile sound effects
    const jitter =
      effect === 'MOVE' || effect === 'CAPTURE' || effect === 'CARD_DRAW_NUMBER'
        ? 0.97 + Math.random() * 0.06
        : 1.0;
    src.playbackRate.value = jitter;

    const gain = ctx.createGain();
    gain.gain.value = effect === 'PIECE_SELECT' ? 0.55 : 0.72;
    src.connect(gain);
    gain.connect(ctx.destination);
    src.start();
  }

  playSequence(effects: SoundEffect[]): void {
    if (this.muted || effects.length === 0) return;
    effects.forEach((effect, index) => {
      if (index === 0) {
        this.play(effect);
      } else {
        window.setTimeout(() => this.play(effect), index * STAGGER_MS);
      }
    });
  }
}

export const soundManager = new WebSoundManager();
