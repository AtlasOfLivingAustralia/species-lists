import {Accordion} from "@mantine/core";
import {Link} from "react-router-dom";
import {IconHomeCog, IconSettings} from "@tabler/icons-react";
import {AllLists, UploadListOption} from "./SpeciesListSideBar.tsx";
import {useAuth} from "../helpers/useAuth.ts";

export function SpeciesListsSideBar({ resetSpeciesList }: { resetSpeciesList: () => void }) {
    const {currentUser} = useAuth();

    return (
        <Accordion defaultValue="selected-list" styles={{ chevron: { display: "none" } }}>
            <AllLists resetSpeciesList={resetSpeciesList} />
            <UploadListOption resetSpeciesList={resetSpeciesList} />
            {currentUser &&
                <Accordion.Item value="my-lists">
                    <Link to={`/my-lists`}>
                        <Accordion.Control icon={<IconHomeCog />}>My lists</Accordion.Control>
                    </Link>
                </Accordion.Item>
            }
            {currentUser?.isAdmin &&
                <Accordion.Item value="admin">
                    <Link to={`/admin`}>
                        <Accordion.Control icon={<IconSettings />}>Admin</Accordion.Control>
                    </Link>
                </Accordion.Item>
            }
        </Accordion>
    );
}