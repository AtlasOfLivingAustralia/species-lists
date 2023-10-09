import {SpeciesListItemProps} from "../api/sources/props.ts";
import {useState} from "react";
import {TaxonImages} from "./TaxonImage.tsx";
import {Anchor, Space, Switch, Table, Text, Title} from "@mantine/core";
import {FormattedMessage} from "react-intl";

export function SpeciesListItemView({ selectedItem, customFields }: SpeciesListItemProps) {

    const [showInterpretation, setShowInterpretation] = useState<boolean>(false);

    return (
        <>
            <TaxonImages taxonID={selectedItem?.classification?.taxonConceptID || ''} />

            <Space h="lg" />

            <Title order={3}>Taxonomy</Title>
            <Space h="md" />

            {!showInterpretation && selectedItem?.classification && (
                <Table striped highlightOnHover withBorder>
                    <tbody>
                    <tr>
                        <td>Supplied scientificName</td>
                        <td>{selectedItem.scientificName}</td>
                    </tr>
                    <tr>
                        <td>scientificName</td>
                        <td>
                            <Anchor href={'https://bie.ala.org.au/species/' + selectedItem.classification.taxonConceptID}>
                                <i>{selectedItem.classification?.scientificName}</i>
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>vernacularName</td>
                        <td>{selectedItem.classification?.vernacularName}</td>
                    </tr>
                    <tr>
                        <td>family</td>
                        <td>
                            <Anchor href={import.meta.env.VITE_APP_BIE_URL + '/species/' + selectedItem.classification.familyID}>
                                {selectedItem.classification?.family}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>kingdom</td>
                        <td>
                            <Anchor href={import.meta.env.VITE_APP_BIE_URL + '/species/' + selectedItem.classification.kingdomID}>
                                {selectedItem.classification?.kingdom}
                            </Anchor>
                        </td>
                    </tr>
                    </tbody>
                </Table>
            )}

            {showInterpretation && (
                <Table striped highlightOnHover withBorder>
                    <thead>
                    <tr key="interpretation-header">
                        <th key="no-header"></th>
                        <th key="supplied-header">Supplied</th>
                        <th key="matched-header">Matched</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr>
                        <td>scientificName</td>
                        <td>{selectedItem?.scientificName}</td>
                        <td>
                            <Anchor href={import.meta.env.VITE_APP_BIE_URL + '/species/' + selectedItem?.classification.taxonConceptID}>
                                <i>{selectedItem?.classification?.scientificName}</i>
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>vernacularName</td>
                        <td>{selectedItem?.vernacularName}</td>
                        <td>{selectedItem?.classification?.vernacularName}</td>
                    </tr>
                    <tr>
                        <td>genus</td>
                        <td>{selectedItem?.genus}</td>
                        <td>
                            <Anchor href={import.meta.env.VITE_APP_BIE_URL + '/species/' + selectedItem.classification.genusID}>
                                {selectedItem?.classification?.genus}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>family</td>
                        <td>{selectedItem?.family}</td>
                        <td>
                            <Anchor href={import.meta.env.VITE_APP_BIE_URL + '/species/' + selectedItem.classification.familyID}>
                                {selectedItem?.classification?.family}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>order</td>
                        <td>{selectedItem?.order}</td>
                        <td>
                            <Anchor href={import.meta.env.VITE_APP_BIE_URL + '/species/' + selectedItem.classification.orderID}>
                                {selectedItem?.classification?.order}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>class</td>
                        <td>{selectedItem?.classs}</td>
                        <td>
                            <Anchor href={import.meta.env.VITE_APP_BIE_URL + '/species/' + selectedItem.classification.classID}>
                                {selectedItem?.classification?.classs}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>phylum</td>
                        <td>{selectedItem?.phylum}</td>
                        <td>
                            <Anchor href={import.meta.env.VITE_APP_BIE_URL + '/species/' + selectedItem.classification.phylumID}>
                                {selectedItem?.classification?.phylum}
                            </Anchor>
                        </td>
                    </tr>
                    <tr>
                        <td>kingdom</td>
                        <td>{selectedItem?.kingdom}</td>
                        <td>
                            <Anchor href={import.meta.env.VITE_APP_BIE_URL + '/species/' + selectedItem.classification.kingdomID}>
                                {selectedItem?.classification?.kingdom}
                            </Anchor>
                        </td>
                    </tr>
                    </tbody>
                </Table>
            )}

            <Text style={{ paddingTop: '15px', paddingBottom: '15px' }}>
                <FormattedMessage id="interpreted.values.warning" />
            </Text>

            <Switch
                id="interpretation"
                label="Show full taxonomy"
                checked={showInterpretation}
                onChange={() => setShowInterpretation(!showInterpretation)}
            />

            {customFields && customFields.length > 0 && <>
                <Space h="md" />

                <Title order={3}>Properties</Title>
                <Space h="md" />
                <Table striped highlightOnHover>
                    <thead>
                    <tr>
                        <th key={`customPropertyHdr`}>Property</th>
                        <th key={`customPropertyValueHdr`}>Value</th>
                    </tr>
                    </thead>
                    <tbody>
                    {customFields?.map((customField) => (
                        <tr key={customField}>
                            <td>{customField}</td>
                            <td>{selectedItem?.properties.find((element) => element.key === customField)?.value}</td>
                        </tr>
                    ))}
                    </tbody>
                </Table>
                <div style={{ height: '300px' }}></div>
                <Space h="md" />
            </>}
        </>
    );
}