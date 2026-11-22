import React from 'react';
import { PieceColor, PieceType } from '../model/gameModels';

interface ChessPieceGraphicProps {
  type: PieceType;
  color: PieceColor;
  size?: number | string;
  className?: string;
}

export const ChessPieceGraphic: React.FC<ChessPieceGraphicProps> = ({
  type,
  color,
  size = '100%',
  className,
}) => {
  const isWhite = color === 'WHITE';
  const fill = isWhite ? '#FAF8F3' : '#1E1E22';
  const stroke = isWhite ? '#1B1B1E' : '#EAE8E1';
  const accent = isWhite ? '#D5CFC2' : '#3C3C44';

  return (
    <svg
      viewBox="0 0 100 100"
      width={size}
      height={size}
      className={className}
      style={{
        display: 'block',
        filter: 'drop-shadow(0 1.5px 1.8px rgba(0,0,0,0.22))',
        overflow: 'visible',
      }}
      aria-hidden="true"
    >
      {type === 'PAWN' && (
        <g stroke={stroke} strokeWidth="4.2" strokeLinejoin="round" strokeLinecap="round">
          <path d="M 22 88 L 78 88 L 75 78 Q 62 74 58 64 L 55 46 L 45 46 L 42 64 Q 38 74 25 78 Z" fill={fill} />
          <path d="M 38 46 Q 50 51 62 46" fill="none" stroke={accent} strokeWidth="3.2" />
          <circle cx="50" cy="30" r="14" fill={fill} />
        </g>
      )}

      {type === 'ROOK' && (
        <g stroke={stroke} strokeWidth="4.2" strokeLinejoin="round" strokeLinecap="round">
          <path
            d="M 20 88 L 80 88 L 76 76 L 68 70 L 66 36 L 74 30 L 74 16 L 62 16 L 62 24 L 54 24 L 54 16 L 46 16 L 46 24 L 38 24 L 38 16 L 26 16 L 26 30 L 34 36 L 32 70 L 24 76 Z"
            fill={fill}
          />
          <line x1="34" y1="36" x2="66" y2="36" stroke={accent} strokeWidth="3" />
          <line x1="32" y1="70" x2="68" y2="70" stroke={accent} strokeWidth="3" />
        </g>
      )}

      {type === 'KNIGHT' && (
        <g stroke={stroke} strokeWidth="4.2" strokeLinejoin="round" strokeLinecap="round">
          <path
            d="M 22 88 L 78 88 L 75 76 L 68 68 C 72 52 72 34 62 22 L 56 14 L 49 20 C 37 22 28 32 22 44 L 27 52 L 38 46 C 36 56 32 64 25 76 Z"
            fill={fill}
          />
          <path d="M 55 26 C 63 36 64 52 59 68" fill="none" stroke={accent} strokeWidth="3.4" />
          <circle cx="40" cy="34" r="2.6" fill={stroke} stroke="none" />
        </g>
      )}

      {type === 'BISHOP' && (
        <g stroke={stroke} strokeWidth="4.2" strokeLinejoin="round" strokeLinecap="round">
          <path
            d="M 20 88 L 80 88 L 74 77 L 64 70 C 66 54 68 40 50 22 C 32 40 34 54 36 70 L 26 77 Z"
            fill={fill}
          />
          <circle cx="50" cy="15" r="5.5" fill={fill} />
          <line x1="44" y1="37" x2="56" y2="49" stroke={stroke} strokeWidth="3.6" />
          <line x1="56" y1="37" x2="44" y2="49" stroke={stroke} strokeWidth="3.6" />
          <line x1="36" y1="70" x2="64" y2="70" stroke={accent} strokeWidth="3" />
        </g>
      )}

      {type === 'QUEEN' && (
        <g stroke={stroke} strokeWidth="4" strokeLinejoin="round" strokeLinecap="round">
          <path
            d="M 20 88 L 80 88 L 74 75 L 82 32 L 64 52 L 50 22 L 36 52 L 18 32 L 26 75 Z"
            fill={fill}
          />
          <circle cx="18" cy="27" r="4.8" fill={fill} />
          <circle cx="50" cy="17" r="5.2" fill={fill} />
          <circle cx="82" cy="27" r="4.8" fill={fill} />
          <circle cx="34" cy="23" r="4.4" fill={fill} />
          <circle cx="66" cy="23" r="4.4" fill={fill} />
          <line x1="26" y1="75" x2="74" y2="75" stroke={accent} strokeWidth="3" />
        </g>
      )}

      {type === 'KING' && (
        <g stroke={stroke} strokeWidth="4.2" strokeLinejoin="round" strokeLinecap="round">
          <line x1="50" y1="8" x2="50" y2="26" stroke={stroke} strokeWidth="4.8" />
          <line x1="42" y1="15" x2="58" y2="15" stroke={stroke} strokeWidth="4.8" />
          <path
            d="M 20 88 L 80 88 L 74 74 C 82 54 78 32 62 32 C 55 32 52 36 50 40 C 48 36 45 32 38 32 C 22 32 18 54 26 74 Z"
            fill={fill}
          />
          <line x1="50" y1="38" x2="50" y2="74" stroke={accent} strokeWidth="3.2" />
          <line x1="26" y1="74" x2="74" y2="74" stroke={accent} strokeWidth="3" />
        </g>
      )}
    </svg>
  );
};
