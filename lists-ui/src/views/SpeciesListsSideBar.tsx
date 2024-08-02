import {Accordion} from "@mantine/core";
import {Link} from "react-router-dom";
import {IconHomeCog, IconLock, IconSettings} from "@tabler/icons-react";
import {AllLists, UploadListOption} from "./SpeciesListSideBar.tsx";
import {useContext} from "react";
import UserContext from "../helpers/UserContext.ts";
import {ListsUser} from "../api/sources/model.ts";

interface SpeciesListsSideBarProps {
    resetSpeciesList: () => void;
    selectedView: string;
}

export function SpeciesListsSideBar( {resetSpeciesList, selectedView} : SpeciesListsSideBarProps){

    const currentUser = useContext(UserContext) as ListsUser;
    return (
        <Accordion className={`speciesListsSideBar`} value={selectedView} >
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
                <Accordion.Item value="private-lists">
                    <Link to={`/private-lists`}>
                        <Accordion.Control icon={<IconLock />}>Private lists</Accordion.Control>
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