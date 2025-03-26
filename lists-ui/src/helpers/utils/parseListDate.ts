import { parse } from 'date-fns';

// example dateString from GraphQL output
// 'Wed Jan 29 03:01:57 AEDT 2025'
const formatString = "EEE MMM dd HH:mm:ss yyyy";


/**
 * Parse the date string from the GraphQL output
 * into a Date object
 * 
 * @param dateString 
 * @returns 
 */
export const parseDate = (dateString: string): Date | undefined => {
  try {
    // Remove timezone from dateString (AEDT, etc)
    const cleanedDateString = dateString.replace(/\s[A-Z]{3,4}\s/, ' ');
    const parsedDate = parse(cleanedDateString, formatString, new Date());
    return parsedDate;
  } catch (error) {
    console.error('Error parsing date:', error);
  }
};
