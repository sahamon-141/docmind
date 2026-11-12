import { describe, expect, it } from 'vitest';
import { precheckFile } from './useUploader';

const file = (name: string, bytes: number) => new File([new Uint8Array(bytes)], name);

describe('precheckFile', () => {
  it('rejects files above the size limit', () => {
    expect(precheckFile(file('big.pdf', 10 * 1024 * 1024 + 1))).toBe('Larger than the 10 MB limit.');
    expect(precheckFile(file('ok.pdf', 2048), 1024)).toBe('Larger than the 1.0 KB limit.');
  });

  it('accepts supported files within the limit', () => {
    expect(precheckFile(file('notes.md', 100))).toBeNull();
  });

  it('rejects unsupported and empty files', () => {
    expect(precheckFile(file('image.png', 100))).toMatch(/^Unsupported type/);
    expect(precheckFile(file('empty.txt', 0))).toBe('The file is empty.');
  });
});
