import { GameUiState } from '../model/gameModels';

export type SoundEffect =
  | 'CARD_DRAW_NUMBER'
  | 'CARD_DRAW_SPECIAL'
  | 'PIECE_SELECT'
  | 'MOVE'
  | 'CAPTURE'
  | 'CASTLE'
  | 'CHECK'
  | 'VICTORY'
  | 'DEFEAT'
  | 'DRAW_STALEMATE'
  | 'REVIVE'
  | 'FREEZE'
  | 'SHIELD'
  | 'MERGE'
  | 'SACRIFICE'
  | 'SWAP'
  | 'CLONE'
  | 'HIJACK'
  | 'PROMOTE'
  | 'SUICIDE'
  | 'WILD'
  | 'SNIPER'
  | 'PORTAL'
  | 'TRAP_ARM'
  | 'TRAP_TRIGGER'
  | 'DRAW_TWO'
  | 'ALERT';

export function detectSoundEffects(prev: GameUiState, next: GameUiState): SoundEffect[] {
  const effects: SoundEffect[] = [];

  // 1. Card draw detected
  if (next.drawRevision > prev.drawRevision && next.activeCard !== null) {
    const type = next.activeCard.type;
    if (type.kind === 'NUMBER') {
      effects.push('CARD_DRAW_NUMBER');
    } else if (type.kind === 'WILD') {
      effects.push('WILD');
    } else {
      effects.push('CARD_DRAW_SPECIAL');
    }
  }

  // 2. Move / Capture / Castle / Swap / Hijack animation detected
  const moveAnim = next.moveAnimationEvent;
  if (moveAnim && moveAnim.id !== prev.moveAnimationEvent?.id) {
    if (prev.phase === 'SWAPPING') {
      effects.push('SWAP');
    } else if (prev.phase === 'HIJACKING') {
      effects.push('HIJACK');
    } else if (moveAnim.secondaryPiece != null) {
      effects.push('CASTLE');
    } else if (moveAnim.isCapture) {
      effects.push('CAPTURE');
    } else {
      effects.push('MOVE');
    }
  }

  // 3. Revive / Merge / Sacrifice / Promote / Clone pop animation detected
  const popAnim = next.reviveAnimationEvent;
  if (popAnim && popAnim.id !== prev.reviveAnimationEvent?.id) {
    switch (prev.phase) {
      case 'REVIVING':
        effects.push('REVIVE');
        break;
      case 'MERGING':
        effects.push('MERGE');
        break;
      case 'SACRIFICING':
        effects.push('SACRIFICE');
        break;
      case 'PROMOTING_PAWN':
        effects.push('PROMOTE');
        break;
      case 'CLONING':
        effects.push('CLONE');
        break;
      default:
        effects.push('REVIVE');
        break;
    }
  }

  // 4. Freeze applied
  if (prev.phase === 'FREEZING' && next.phase === 'MOVING' && next.frozenPieceId !== null) {
    effects.push('FREEZE');
  }

  // 5. Shield applied
  if (prev.phase === 'SHIELDING' && next.phase === 'MOVING' && next.shieldedPieceId !== null) {
    effects.push('SHIELD');
  }

  // 5b. Sniper shot executed
  if (prev.phase === 'SNIPING' && next.phase !== 'SNIPING') {
    effects.push('SNIPER');
  }

  // 5c. Portal teleport executed
  if (prev.phase === 'PORTALING' && next.phase !== 'PORTALING') {
    effects.push('PORTAL');
  }

  // 5d. Trap armed
  if (prev.phase === 'TRAPPING' && next.phase !== 'TRAPPING') {
    effects.push('TRAP_ARM');
  }

  // 5e. Trap triggered
  if (prev.traps.length > next.traps.length && prev.phase !== 'TRAPPING') {
    effects.push('TRAP_TRIGGER');
  }

  // 5f. Draw two chosen
  if (prev.phase === 'DRAW_TWO_CHOOSING' && next.phase !== 'DRAW_TWO_CHOOSING') {
    effects.push('DRAW_TWO');
  }

  // 6. Suicide elimination
  if (prev.phase === 'SUICIDING' && next.phase !== 'SUICIDING') {
    effects.push('SUICIDE');
  }

  // 7. Game over outcome
  if (prev.outcome === null && next.outcome !== null) {
    if (next.outcome.kind === 'STALEMATE') {
      effects.push('DRAW_STALEMATE');
    } else if (next.outcome.kind === 'SUICIDE') {
      effects.push('DEFEAT');
    } else {
      effects.push('VICTORY');
    }
    return effects;
  }

  // 8. Check given
  if (next.checkedKingSquare !== null && prev.checkedKingSquare === null) {
    effects.push('CHECK');
  }

  // 9. Piece tap selection (quiet subtle tick when nothing bigger fired)
  if (
    effects.length === 0 &&
    next.selectedSquare !== null &&
    (prev.selectedSquare === null ||
      prev.selectedSquare.row !== next.selectedSquare.row ||
      prev.selectedSquare.col !== next.selectedSquare.col)
  ) {
    effects.push('PIECE_SELECT');
  }

  // 10. Alert notice
  if (
    effects.length === 0 &&
    next.noticeBanner !== null &&
    next.noticeBanner.isAlert &&
    next.noticeBanner.id !== prev.noticeBanner?.id
  ) {
    effects.push('ALERT');
  }

  return effects;
}
