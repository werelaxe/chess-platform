// Square arithmetic shared by the board and the game wrapper. index = rank * 8 + file.

export const FILES = ["a", "b", "c", "d", "e", "f", "g", "h"] as const;

export function fileOf(index: number): number {
  return index & 7;
}

export function rankOf(index: number): number {
  return index >> 3;
}

export function squareAt(file: number, rank: number): number {
  return rank * 8 + file;
}

export function squareName(index: number): string {
  return `${FILES[fileOf(index)]}${rankOf(index) + 1}`;
}

export function squareIndex(name: string): number {
  if (name.length !== 2) throw new Error(`Invalid square name: '${name}'`);
  const file = name.charCodeAt(0) - 97;
  const rank = name.charCodeAt(1) - 49;
  if (file < 0 || file > 7 || rank < 0 || rank > 7) throw new Error(`Invalid square name: '${name}'`);
  return squareAt(file, rank);
}

export function isLightSquare(index: number): boolean {
  return (fileOf(index) + rankOf(index)) % 2 === 1;
}
