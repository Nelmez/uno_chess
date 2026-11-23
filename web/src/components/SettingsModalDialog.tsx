import React from 'react';
import { Volume2, VolumeX, Sun, Moon, X, Check } from 'lucide-react';
import {
  GAME_MODE_META,
  GameMode,
  UNO_CARD_THEME_META,
  UnoCardTheme,
} from '../model/gameModels';
import { UnoCardThemePreviewPair } from './UnoCardComponents';

interface SettingsModalDialogProps {
  gameMode: GameMode;
  isDarkTheme: boolean;
  cardTheme: UnoCardTheme;
  isSoundMuted: boolean;
  isOnlineMultiplayer: boolean;
  onGameModeChanged: (mode: GameMode) => void;
  onThemeChanged: (isDark: boolean) => void;
  onCardThemeChanged: (theme: UnoCardTheme) => void;
  onSoundMutedChanged: (muted: boolean) => void;
  onDismiss: () => void;
}

const GAME_MODES: GameMode[] = ['PASS_AND_PLAY', 'VS_AI_EASY', 'VS_AI_MEDIUM', 'VS_AI_HARD'];
const CARD_THEMES: UnoCardTheme[] = ['CLASSIC', 'SPACE', 'FANTASY', 'SPIRITS'];

export const SettingsModalDialog: React.FC<SettingsModalDialogProps> = ({
  gameMode,
  isDarkTheme,
  cardTheme,
  isSoundMuted,
  isOnlineMultiplayer,
  onGameModeChanged,
  onThemeChanged,
  onCardThemeChanged,
  onSoundMutedChanged,
  onDismiss,
}) => {
  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '14px 16px',
            borderBottom: '1px solid var(--hairline)',
          }}
        >
          <h3 className="font-display" style={{ margin: 0, fontSize: '18px' }}>
            Settings
          </h3>
          <button type="button" onClick={onDismiss} className="icon-btn-48" aria-label="Close">
            <X size={20} />
          </button>
        </div>

        <div
          style={{
            padding: '14px 16px 18px',
            overflowY: 'auto',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px',
          }}
        >
          {/* Opponent / Mode */}
          <section style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '12px', fontWeight: 800, color: 'var(--ink-muted)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                Game Mode
              </span>
              {isOnlineMultiplayer && (
                <span style={{ fontSize: '11px', color: 'var(--accent-alert)', fontWeight: 600 }}>
                  Changing mode leaves online match
                </span>
              )}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '8px' }}>
              {GAME_MODES.map((mode) => {
                const selected = !isOnlineMultiplayer && gameMode === mode;
                return (
                  <button
                    key={mode}
                    type="button"
                    data-testid={`mode_${mode.toLowerCase()}`}
                    onClick={() => onGameModeChanged(mode)}
                    style={{
                      padding: '10px 12px',
                      borderRadius: '10px',
                      background: selected ? 'var(--accent-primary)' : 'var(--surface-subtle)',
                      color: selected ? 'var(--on-accent-primary)' : 'var(--ink-primary)',
                      border: `1px solid ${selected ? 'var(--accent-primary)' : 'var(--hairline)'}`,
                      fontSize: '12px',
                      fontWeight: 700,
                      textAlign: 'left',
                    }}
                  >
                    {GAME_MODE_META[mode].label}
                  </button>
                );
              })}
            </div>
          </section>

          {/* Appearance & Sound */}
          <section style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '10px' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <span style={{ fontSize: '12px', fontWeight: 800, color: 'var(--ink-muted)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                Appearance
              </span>
              <div style={{ display: 'flex', gap: '6px' }}>
                <button
                  type="button"
                  data-testid="theme_light_btn"
                  onClick={() => onThemeChanged(false)}
                  className="pill-btn"
                  style={{
                    flex: 1,
                    background: !isDarkTheme ? 'var(--accent-primary)' : 'var(--surface-subtle)',
                    color: !isDarkTheme ? 'var(--on-accent-primary)' : 'var(--ink-primary)',
                    border: '1px solid var(--hairline)',
                    fontSize: '12px',
                  }}
                >
                  <Sun size={14} /> Light
                </button>
                <button
                  type="button"
                  data-testid="theme_dark_btn"
                  onClick={() => onThemeChanged(true)}
                  className="pill-btn"
                  style={{
                    flex: 1,
                    background: isDarkTheme ? 'var(--accent-primary)' : 'var(--surface-subtle)',
                    color: isDarkTheme ? 'var(--on-accent-primary)' : 'var(--ink-primary)',
                    border: '1px solid var(--hairline)',
                    fontSize: '12px',
                  }}
                >
                  <Moon size={14} /> Dark
                </button>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <span style={{ fontSize: '12px', fontWeight: 800, color: 'var(--ink-muted)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                Sound Effects
              </span>
              <div style={{ display: 'flex', gap: '6px' }}>
                <button
                  type="button"
                  data-testid="sound_on_btn"
                  onClick={() => onSoundMutedChanged(false)}
                  className="pill-btn"
                  style={{
                    flex: 1,
                    background: !isSoundMuted ? 'var(--accent-primary)' : 'var(--surface-subtle)',
                    color: !isSoundMuted ? 'var(--on-accent-primary)' : 'var(--ink-primary)',
                    border: '1px solid var(--hairline)',
                    fontSize: '12px',
                  }}
                >
                  <Volume2 size={14} /> On
                </button>
                <button
                  type="button"
                  data-testid="sound_mute_btn"
                  onClick={() => onSoundMutedChanged(true)}
                  className="pill-btn"
                  style={{
                    flex: 1,
                    background: isSoundMuted ? 'var(--accent-primary)' : 'var(--surface-subtle)',
                    color: isSoundMuted ? 'var(--on-accent-primary)' : 'var(--ink-primary)',
                    border: '1px solid var(--hairline)',
                    fontSize: '12px',
                  }}
                >
                  <VolumeX size={14} /> Muted
                </button>
              </div>
            </div>
          </section>

          {/* UNO Card Themes */}
          <section style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <span style={{ fontSize: '12px', fontWeight: 800, color: 'var(--ink-muted)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
              UNO Card Theme
            </span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {CARD_THEMES.map((theme) => {
                const selected = cardTheme === theme;
                const meta = UNO_CARD_THEME_META[theme];
                return (
                  <button
                    key={theme}
                    type="button"
                    data-testid={`card_theme_${theme.toLowerCase()}`}
                    onClick={() => onCardThemeChanged(theme)}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '12px',
                      padding: '10px 12px',
                      borderRadius: '12px',
                      background: selected ? 'var(--surface-subtle)' : 'var(--surface)',
                      border: `1.5px solid ${selected ? 'var(--accent-primary)' : 'var(--hairline)'}`,
                      textAlign: 'left',
                    }}
                  >
                    <UnoCardThemePreviewPair theme={theme} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span className="font-display" style={{ fontSize: '14px', fontWeight: 700 }}>
                          {meta.displayName}
                        </span>
                        {selected && <Check size={15} color="var(--accent-success)" />}
                      </div>
                      <div style={{ fontSize: '11px', color: 'var(--ink-muted)', marginTop: '2px' }}>
                        {meta.description}
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          </section>

          <div
            style={{
              textAlign: 'center',
              padding: '16px 0 6px',
              fontSize: '11px',
              color: 'var(--ink-muted)',
              borderTop: '1px solid var(--hairline)',
              marginTop: '8px',
            }}
          >
            UNO Chess Web v1.4.2 · Online Multiplayer & PWA
          </div>
        </div>
      </div>
    </div>
  );
};
