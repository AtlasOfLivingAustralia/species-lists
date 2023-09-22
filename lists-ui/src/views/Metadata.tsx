import {useContext, useEffect, useRef, useState} from "react";
import { useParams } from "react-router-dom";
import { gql, useMutation, useQuery } from "@apollo/client";
import { useIntl } from "react-intl";
import {
    Affix,
    Button,
    Grid,
    Group,
    Skeleton,
    Space,
    Switch,
    Title,
} from "@mantine/core";
import { MetadataForm } from "./MetadataForm";
import SpeciesListSideBar from "./SpeciesListSideBar";
import {ListsUser, SpeciesList} from "../api/sources/model";
import {MetadataProps} from "../api/sources/props";
import LicenceLink from "../components/LicenceLink.tsx";
import {IconLock, IconLockOpen} from "@tabler/icons-react";
import UserContext from "../helpers/UserContext.ts";
import mapboxgl from 'mapbox-gl';
import * as wkt from 'wkt';
import 'mapbox-gl/dist/mapbox-gl.css';
import geojsonExtent from '@mapbox/geojson-extent';
import { useClipboard } from '@mantine/hooks';
import {Geometry} from "geojson";

mapboxgl.accessToken = import.meta.env.VITE_APP_MAPBOX_TOKEN;

export function Metadata({ setSpeciesList }: MetadataProps): JSX.Element {

    const clipboard = useClipboard({ timeout: 1000 });
    const {speciesListID} = useParams<{ speciesListID: string }>();
    const [edit, setEdit] = useState(false);
    const currentUser = useContext(UserContext) as ListsUser;

    const mapContainer = useRef<HTMLDivElement | null>(null);
    const map = useRef<mapboxgl.Map | null>(null);
    const [lng ] = useState(133);
    const [lat ] = useState(-24.5);
    const [zoom] = useState(3);


    const GET_LIST = gql`
        query loadList($speciesListID: String!) {
            getSpeciesListMetadata(speciesListID: $speciesListID) {
                id
                title
                description
                licence
                rowCount
                fieldList
                listType
                doi
                authority
                region
                isAuthoritative
                isPrivate
                isInvasive
                isThreatened
                isBIE
                isSDS
                createDate
                lastModifiedDate
                lastUpdatedBy
                owner
                editors
                wkt
            }
        }
    `;

    const UPDATE_LIST = gql`
        mutation update(
            $id: String!
            $title: String!
            $description: String
            $licence: String!
            $listType: String!
            $authority: String
            $region: String
            $isAuthoritative: Boolean
            $isPrivate: Boolean
            $isSDS: Boolean
            $isBIE: Boolean
            $wkt: String
        ) {
            updateMetadata(
                id: $id
                title: $title
                description: $description
                licence: $licence
                listType: $listType
                authority: $authority
                region: $region
                isAuthoritative: $isAuthoritative
                isPrivate: $isPrivate
                isSDS: $isSDS
                isBIE: $isBIE
                wkt: $wkt
            ) {
                id
                title
                description
                licence
                rowCount
                fieldList
                listType
                doi
                authority
                region
                isAuthoritative
                isPrivate
                isInvasive
                isThreatened
                isSDS
                isBIE
                createDate
                lastModifiedDate
                owner
                editors
                wkt
            }
        }
    `;

    const intl = useIntl();

    const {loading, error, data} = useQuery<{ getSpeciesListMetadata: SpeciesList }>(GET_LIST, {
        variables: {
            speciesListID: speciesListID,
        },
        errorPolicy: "all", // Add errorPolicy if needed
    });

    const [updateList] = useMutation(UPDATE_LIST, {
        context: {
            headers: {
                "Authorization": "Bearer " + currentUser?.user?.access_token,
            }
        },
        refetchQueries: [
            {
                query: GET_LIST,
            },
        ],
    });

    function updateMetadata(values: SpeciesList) {
        updateList({variables: values}).then(() => {
            setEdit(false);
        });
    }

    if (error) return <div>Error! {error.message}</div>;

    setSpeciesList(data?.getSpeciesListMetadata);

    const speciesList = data?.getSpeciesListMetadata ? data?.getSpeciesListMetadata as SpeciesList : {} as SpeciesList;

    useEffect(() => {

        if (speciesList?.wkt) {
            if (map.current || !mapContainer.current) return; // initialize map only once
            map.current = new mapboxgl.Map({
                container: mapContainer.current,
                style:  `mapbox://styles/mapbox/light-v11`,
                center: [lng, lat],
                zoom: zoom
            });

            const geometry = wkt.parse(speciesList?.wkt) as Geometry;

            const geojson = {
                type: 'Feature',
                geometry: geometry,
                properties: {}
            };

            map.current.on('load', function () {

                if (map.current) {
                    // @ts-ignore
                    map.current.addSource('checklist_wkt', {
                        type: 'geojson',
                        data: geometry
                    });

                    // Add a new layer to visualize the polygon.
                    map.current.addLayer({
                        'id': 'checklist_wkt',
                        'type': 'fill',
                        'source': 'checklist_wkt', // reference the data source
                        'layout': {},
                        'paint': {
                            'fill-color': '#c44d34', // blue color fill
                            'fill-opacity': 0.5
                        }
                    });

                    // Add a black outline around the polygon.
                    map.current.addLayer({
                        'id': 'outline',
                        'type': 'line',
                        'source': 'checklist_wkt',
                        'layout': {},
                        'paint': {
                            'line-color': '#000',
                            'line-width': 3
                        }
                    });

                    if (geometry) {
                        let bb = geojsonExtent(geojson);
                        map.current.fitBounds(bb);
                    }
                }
            });
        }
    });

    return (
        <Grid mb="md" align="flex-start">
            <Grid.Col xs={1}  sm={2} style={{backgroundColor: "#F6F6F6"}}>
                <SpeciesListSideBar selectedView="metadata"
                                    resetSpeciesList={() => setSpeciesList(null)}/>
            </Grid.Col>
            <Grid.Col xs={12} sm={4} style={{paddingLeft: "30px"}}>
                <Title order={3}>Metadata for {speciesList?.title}</Title>
                <Space h="md"/>
                { !loading &&
                    <Affix position={{top: 160, right: 20}}>
                        <Switch
                            onLabel="EDIT"
                            offLabel="EDIT"
                            checked={edit}
                            size="lg"
                            disabled={
                                currentUser?.user?.access_token === undefined
                                || (!currentUser.isAdmin && currentUser.userId != speciesList.owner)
                            }
                            onChange={(event) => setEdit(event.currentTarget.checked)}
                        />
                    </Affix>
                }
                <Space h="md"/>

                {loading &&
                    <dl>
                        <dt>Species list name</dt>
                        <dd><Skeleton height={25} width={500} /></dd>

                        <Space h="md"/>
                        <dt>Description</dt>
                        <dd><Skeleton height={25} width={500} /></dd>

                        <Space h="md"/>
                        <dt>Category</dt>
                        <dd><Skeleton height={25} width={500} /></dd>

                        <Space h="md"/>
                        <dt>Licence</dt>
                        <dd><Skeleton height={25} width={500} /></dd>

                        <Space h="md"/>
                        <dt>Visibility</dt>
                        <dd><Skeleton height={25} width={500} /></dd>

                        <Space h="md"/>
                        <dt>Authority</dt>
                        <dd><Skeleton height={25} width={500} /></dd>

                        <Space h="md"/>
                        <dt>Region</dt>
                        <dd><Skeleton height={25} width={500} /></dd>

                        <Space h="md"/>
                        <dt>Created</dt>
                        <dd><Skeleton height={25} width={500} /></dd>

                        <Space h="md"/>
                        <dt>Last modified</dt>
                        <dd><Skeleton height={25} width={500} /></dd>
                    </dl>
                }

                {!loading && <>
                    {edit && (
                        <MetadataForm
                            speciesList={speciesList}
                            submitFormFcn={updateMetadata}
                            edit={edit}
                            suppliedFields={speciesList?.fieldList}
                            resetUpload={() => console.log('reset upload') }
                            formButtons={
                                <Group position="center" mt="xl">
                                    <Button variant="outline" onClick={() => setEdit(false)}>Cancel</Button>
                                    <Button variant="outline" type="submit">Update list</Button>
                                </Group>
                            }
                        />
                    )}
                    {!edit && (
                        <>
                        <dl>
                            <dt>Species list name</dt>
                            <dd>{speciesList?.title}</dd>

                            <Space h="md"/>
                            <dt>Description</dt>
                            <dd>{speciesList?.description ? speciesList?.description : 'Not specified'}</dd>

                            <Space h="md"/>
                            <dt>Category</dt>
                            <dd>{intl.formatMessage({id: speciesList?.listType})}</dd>

                            <Space h="md"/>
                            <dt>Licence</dt>
                            <dd>
                                <LicenceLink licenceAcronym={speciesList?.licence} />
                            </dd>

                            <Space h="md"/>
                            <dt>Visibility</dt>
                            <dd>{speciesList.isPrivate ? <IconLock/> : <IconLockOpen/>} {speciesList?.isPrivate ? 'Private' : 'Public'}</dd>

                            <Space h="md"/>
                            <dt>Authority</dt>
                            <dd>{speciesList?.authority ? speciesList?.authority : 'Not specified'}</dd>

                            <Space h="md"/>
                            <dt>Region</dt>
                            <dd>{speciesList?.region ? speciesList?.region: 'Not specified'}</dd>

                            <Space h="md"/>
                            <dt>Created</dt>
                            <dd>{speciesList?.createDate}</dd>

                            <Space h="md"/>
                            <dt>Well Known Text</dt>
                            <dd>
                                { !speciesList.wkt &&
                                    <>Not specified</>
                                }
                                {speciesList?.wkt &&
                                    <Button
                                        variant="outline"
                                        onClick={() => clipboard.copy(speciesList?.wkt)}>
                                        {clipboard.copied ? 'Copied to clipboard'  : 'Copy to clipboard'}
                                    </Button>
                                }
                            </dd>

                            <Space h="md"/>
                            <dt>Last modified</dt>
                            <dd>{speciesList?.lastModifiedDate}</dd>

                            {speciesList?.lastUpdatedBy && <>
                                <Space h="md"/>
                                <dt>Last modified by</dt>
                                <dd>{speciesList?.lastUpdatedBy}</dd>
                                </>
                            }
                        </dl>
                        </>
                    )}
                </>}
            </Grid.Col>
            <Grid.Col xs={12} sm={6}>
                <div
                    style={{ marginTop:'60px', width: '100%', height: '500px'}}
                    ref={mapContainer} className="map-container"
                />
            </Grid.Col>
        </Grid>
    );
}
