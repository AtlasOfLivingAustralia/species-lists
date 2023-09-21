import {useIntl} from "react-intl";
import {Group} from "@mantine/core";

export default function LicenceLink({ licenceAcronym }: { licenceAcronym: string | undefined }) {
    const intl = useIntl();

    if (!licenceAcronym) {
        return <>Not specified</>;
    }

    if (licenceAcronym === 'CC0') {
        return <>
            <Group>
            <img src="https://licensebuttons.net/l/zero/1.0/88x31.png"/>
            Creative Commons Zero
            </Group>
        </>;
    }
    if (licenceAcronym === 'CC-BY') {
        return <>
            <Group>
            <img src="https://licensebuttons.net/l/zero/1.0/88x31.png"/>
            Creative Commons By Attribution
            </Group>
        </>;
    }
    if (licenceAcronym === 'CC-BY-NC') {
        return <>
            <Group>
                <img src="https://licensebuttons.net/l/zero/1.0/88x31.png"/>
                Creative Commons Attribution-Noncommercial
            </Group>
        </>;
    }

    return <>{intl.formatMessage({ id: licenceAcronym })}</>;
}
