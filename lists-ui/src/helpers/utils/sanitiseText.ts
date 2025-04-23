
export default function sanitiseText(input: string | undefined): string | undefined {
  if (!input) return undefined;

  // Remove any HTML tags
  const noHtml = input.replace(/<[^>]*>/g, '');
  return noHtml.trim();
}