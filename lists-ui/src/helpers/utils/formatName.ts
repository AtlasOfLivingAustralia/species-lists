
const ITALIC_RANKS = [
  'genus',
  'subgenus',
  'series',
  'subseries',
  'infraspecies',
  'species',
  'subspecies',
  'variety',
  'form',
  'cultivar',
  'hybrid',
];

/**
 * Format the name of a taxon based on its rankID, falling back to rank if rankID is not available.
 *
 * @param rank The rank of the taxon (e.g., "species", "genus").
 * @param rankID The rank ID of the taxon (e.g., 7000 for species).
 * @param styleName The desired text fontStyle ("italic" or "underline"). Defaults to "italic".
 * @returns The text decoration style to apply, or undefined if no specific style is needed.
 */
export const getStyleForTaxon = (
  rank: string,
  rankID: number | undefined,
  styleName: string = 'italic'
): string | undefined => {
  // If rankID is a number and is greater than or equal to 6000, use the provided styleName.
  if (rankID !== undefined && rankID >= 6000) {
    return styleName;
  }

  // If rank is not provided, we can't determine the style.
  if (!rank) {
    return undefined;
  }

  // Check if the rank should be underlined.
  if (ITALIC_RANKS.includes(rank.toLowerCase())) {
    return styleName;
  }

  // No specific style needed.
  return undefined;
};
