
const validationRules: Record<string, string[]> = {
    "SENSITIVE_LIST": ["generalisation"],
    "CONSERVATION_LIST": ["status"],
    "INVASIVE": ["status"]
}

export function validateListType(listType:string, suppliedFields:string[]) {

    if (!validationRules[listType])
        return null;
    const missingFields = validationRules[listType].filter((field) => !suppliedFields.includes(field));
    if (missingFields && missingFields.length > 0){
        return `${listType}_VALIDATION_FAILED`;
    }
    return null;
}