
export default function sanitiseText(input: string | undefined): string | undefined {
  if (!input) return undefined;

  // Escape HTML special characters to prevent HTML injection
  const escaped = input
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
  return escaped.trim();
}