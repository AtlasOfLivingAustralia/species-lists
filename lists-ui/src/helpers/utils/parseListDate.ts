import { parse } from 'date-fns';

// Supported formats:
// 'Wed Jan 29 03:01:57 AEDT 2025'
// 'Wed Jan 29 03:01:57 GMT+11:00 2025'
// '2025-11-25 10:02:28.0'

const FORMAT_WITH_DAY = "EEE MMM dd HH:mm:ss yyyy";
const FORMAT_ISO_LIKE = "yyyy-MM-dd HH:mm:ss.S";

/**
 * Parse the date string from the GraphQL output
 * into a Date object
 * 
 * @param dateString 
 * @returns 
 */
export const parseDate = (dateString: string): Date | undefined => {
  try {
    // Check if it's the ISO-like format (contains hyphens)
    if (dateString.includes('-')) {
      const parsedDate = parse(dateString, FORMAT_ISO_LIKE, new Date());
      return parsedDate;
    }
    
    // Otherwise, handle the day-of-week format
    const dateParts = dateString.split(' ');
    dateParts.splice(-2, 1); // Remove timezone part
    const cleanedDateString = dateParts.join(' ');
    const parsedDate = parse(cleanedDateString, FORMAT_WITH_DAY, new Date());
    return parsedDate;
  } catch (error) {
    console.error('Error parsing date:', error);
    return undefined;
  }
};