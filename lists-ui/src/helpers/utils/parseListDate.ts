import { parse } from 'date-fns';

// example dateString from GraphQL output
// 'Wed Jan 29 03:01:57 AEDT 2025'
// 'Wed Jan 29 03:01:57 GMT+11:00 2025' 
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
    const dateParts = dateString.split(' ');
    dateParts.splice(-2, 1); // Remove timezone part as its non-standard and varies with data
    const cleanedDateString = dateParts.join(' ');
    const parsedDate = parse(cleanedDateString, formatString, new Date());
    return parsedDate;
  } catch (error) {
    console.error('Error parsing date:', error);
  }
};
