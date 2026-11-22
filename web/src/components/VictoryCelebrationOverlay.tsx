import React, { useEffect, useRef } from 'react';
import {
  colorDisplayName,
  GameMode,
  GameOutcome,
  getActorName,
  PlayerId,
} from '../model/gameModels';
import { ChessPieceGraphic } from './ChessPieceGraphic';

interface VictoryCelebrationOverlayProps {
  outcome: GameOutcome;
  gameMode: GameMode;
  isOnlineMultiplayer: boolean;
  localPlayer: PlayerId | null;
  onDismiss: () => void;
  onNewGame: () => void;
}

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  color: string;
  rotation: number;
  rotSpeed: number;
  shape: 'ribbon' | 'diamond' | 'star';
  alpha: number;
}

const CONFETTI_COLORS = ['#F59E0B', '#EF4444', '#3B82F6', '#10B981', '#EC4899', '#FACC15', '#8B5CF6'];

export const VictoryCelebrationOverlay: React.FC<VictoryCelebrationOverlayProps> = ({
  outcome,
  gameMode,
  isOnlineMultiplayer,
  localPlayer,
  onDismiss,
  onNewGame,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const isStalemate = outcome.kind === 'STALEMATE';

  useEffect(() => {
    if (isStalemate) return;
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animId = 0;
    const dpr = window.devicePixelRatio || 1;
    const resize = () => {
      canvas.width = window.innerWidth * dpr;
      canvas.height = window.innerHeight * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    resize();
    window.addEventListener('resize', resize);

    const cx = window.innerWidth / 2;
    const cy = window.innerHeight * 0.42;
    const shapes: Particle['shape'][] = ['ribbon', 'diamond', 'star'];

    const particles: Particle[] = Array.from({ length: 68 }, (_, idx) => {
      const angle = (Math.PI * 2 * idx) / 68 + (Math.random() - 0.5) * 0.35;
      const speed = 3.5 + Math.random() * 8.5;
      return {
        x: cx,
        y: cy,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed - 4.2,
        size: 6 + Math.random() * 7,
        color: CONFETTI_COLORS[idx % CONFETTI_COLORS.length],
        rotation: Math.random() * Math.PI * 2,
        rotSpeed: (Math.random() - 0.5) * 0.24,
        shape: shapes[idx % 3],
        alpha: 1,
      };
    });

    const startTime = performance.now();

    const draw = (now: number) => {
      const elapsed = (now - startTime) / 1000;
      ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);

      // Dual expanding shockwave rings in first 1.1s
      for (let ringIdx = 0; ringIdx < 2; ringIdx++) {
        const ringT = elapsed - ringIdx * 0.16;
        if (ringT > 0 && ringT < 0.9) {
          const progress = ringT / 0.9;
          const radius = 36 + progress * Math.min(window.innerWidth, window.innerHeight) * 0.42;
          ctx.save();
          ctx.beginPath();
          ctx.arc(cx, cy, radius, 0, Math.PI * 2);
          ctx.strokeStyle = ringIdx === 0 ? '#F59E0B' : '#FFFFFF';
          ctx.globalAlpha = (1 - progress) * 0.5;
          ctx.lineWidth = 4 * (1 - progress) + 1;
          ctx.stroke();
          ctx.restore();
        }
      }

      // Update & draw 68 physics confetti particles
      let anyVisible = false;
      for (const p of particles) {
        p.x += p.vx;
        p.y += p.vy;
        p.vy += 0.19; // gravity
        p.vx *= 0.985; // air drag
        p.rotation += p.rotSpeed;
        if (elapsed > 2.2) {
          p.alpha = Math.max(0, p.alpha - 0.015);
        }
        if (p.alpha > 0.02 && p.y < window.innerHeight + 40) {
          anyVisible = true;
          ctx.save();
          ctx.translate(p.x, p.y);
          ctx.rotate(p.rotation);
          ctx.globalAlpha = p.alpha;
          ctx.fillStyle = p.color;

          if (p.shape === 'ribbon') {
            ctx.fillRect(-p.size / 2, -p.size / 4, p.size, p.size / 2.2);
          } else if (p.shape === 'diamond') {
            ctx.beginPath();
            ctx.moveTo(0, -p.size / 2);
            ctx.lineTo(p.size / 2.4, 0);
            ctx.lineTo(0, p.size / 2);
            ctx.lineTo(-p.size / 2.4, 0);
            ctx.closePath();
            ctx.fill();
          } else {
            ctx.beginPath();
            for (let i = 0; i < 5; i++) {
              const a = (i * Math.PI * 2) / 5 - Math.PI / 2;
              const r = p.size / 2;
              const a2 = a + Math.PI / 5;
              const r2 = r * 0.45;
              if (i === 0) ctx.moveTo(Math.cos(a) * r, Math.sin(a) * r);
              else ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r);
              ctx.lineTo(Math.cos(a2) * r2, Math.sin(a2) * r2);
            }
            ctx.closePath();
            ctx.fill();
          }
          ctx.restore();
        }
      }

      if (anyVisible || elapsed < 1.2) {
        animId = window.requestAnimationFrame(draw);
      }
    };

    animId = window.requestAnimationFrame(draw);
    return () => {
      window.cancelAnimationFrame(animId);
      window.removeEventListener('resize', resize);
    };
  }, [isStalemate]);

  const title = (() => {
    switch (outcome.kind) {
      case 'CHECKMATE':
        return 'CHECKMATE!';
      case 'STALEMATE':
        return 'STALEMATE';
      case 'FORFEIT':
        return 'VICTORY BY FORFEIT';
      case 'SUICIDE':
        return 'SUICIDE VICTORY';
    }
  })();

  const winnerLine = (() => {
    if (outcome.kind === 'STALEMATE') return 'The match ends in a draw.';
    const winnerName = getActorName(
      outcome.winnerPlayer,
      gameMode,
      isOnlineMultiplayer,
      localPlayer
    );
    return `${winnerName} (${colorDisplayName(outcome.winnerColor)}) Wins!`;
  })();

  const crestColor = outcome.kind === 'STALEMATE' ? 'WHITE' : outcome.winnerColor;

  return (
    <div
      data-testid="victory_celebration_overlay"
      className="modal-backdrop"
      style={{ zIndex: 80, background: 'rgba(10, 11, 14, 0.72)' }}
    >
      {!isStalemate && (
        <canvas
          ref={canvasRef}
          style={{
            position: 'fixed',
            inset: 0,
            pointerEvents: 'none',
            zIndex: 81,
          }}
        />
      )}

      <div
        className="anim-victory-pop"
        style={{
          position: 'relative',
          zIndex: 82,
          width: '100%',
          maxWidth: '370px',
          borderRadius: '24px',
          background: 'var(--surface)',
          border: '2px solid rgba(245, 158, 11, 0.7)',
          boxShadow: '0 24px 64px rgba(0,0,0,0.5)',
          padding: '28px 22px 22px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          textAlign: 'center',
          gap: '14px',
          overflow: 'hidden',
        }}
      >
        {/* Shimmer sweep */}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            background:
              'linear-gradient(105deg, transparent 35%, rgba(255,255,255,0.16) 50%, transparent 65%)',
            animation: 'shimmerSweep 2.8s ease-in-out infinite',
            pointerEvents: 'none',
          }}
        />

        {/* Crest emblem with rotating sunburst & halo */}
        <div
          style={{
            position: 'relative',
            width: '104px',
            height: '104px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <svg
            viewBox="0 0 120 120"
            className="anim-spin-slow"
            style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', opacity: 0.4 }}
          >
            {Array.from({ length: 12 }).map((_, i) => (
              <polygon
                key={i}
                points="60,60 55,4 65,4"
                fill={isStalemate ? '#94A3B8' : '#F59E0B'}
                transform={`rotate(${i * 30} 60 60)`}
              />
            ))}
          </svg>
          <div
            className="anim-pulse-ring"
            style={{
              position: 'absolute',
              width: '78px',
              height: '78px',
              borderRadius: '999px',
              background: isStalemate
                ? 'rgba(148, 163, 184, 0.25)'
                : 'rgba(245, 158, 11, 0.28)',
            }}
          />
          <div
            style={{
              position: 'relative',
              width: '66px',
              height: '66px',
              borderRadius: '999px',
              background: 'var(--surface-subtle)',
              border: `2px solid ${isStalemate ? 'var(--hairline)' : '#F59E0B'}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: '10px',
            }}
          >
            <ChessPieceGraphic type="KING" color={crestColor} />
          </div>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <span
            className="font-mono"
            style={{
              fontSize: '12px',
              fontWeight: 800,
              letterSpacing: '0.14em',
              color: isStalemate ? 'var(--ink-muted)' : '#D97706',
            }}
          >
            {title}
          </span>
          <h2
            className="font-display"
            style={{
              margin: 0,
              fontSize: '24px',
              fontWeight: 800,
              color: 'var(--ink-primary)',
            }}
          >
            {winnerLine}
          </h2>
        </div>

        <div style={{ display: 'flex', gap: '10px', width: '100%', marginTop: '6px' }}>
          <button
            type="button"
            data-testid="victory_view_board_btn"
            onClick={onDismiss}
            className="pill-btn"
            style={{
              flex: 1,
              minHeight: '44px',
              background: 'var(--surface-subtle)',
              border: '1px solid var(--hairline)',
              color: 'var(--ink-primary)',
            }}
          >
            View Board
          </button>
          <button
            type="button"
            data-testid="victory_new_game_btn"
            onClick={onNewGame}
            className="pill-btn"
            style={{
              flex: 1,
              minHeight: '44px',
              background: 'var(--accent-primary)',
              color: 'var(--on-accent-primary)',
            }}
          >
            New Game
          </button>
        </div>
      </div>
    </div>
  );
};
