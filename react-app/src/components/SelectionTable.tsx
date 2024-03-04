import Table from 'react-bootstrap/Table';
import { ISelection } from '../interfaces';
import { useEffect } from 'react';
import { getSelections } from './AxiosFunctions';
import SelectionRow from './SelectionRow';

export default function SelectionTable({ selections, selectionsDispatch, setMessage, options }) {

    let data: ISelection[] = []
    useEffect(() => {
        if (selections.length == 0) {
            getSelections()
                .then((response) => {
                    data = response.data
                    for (let i = 0; i < data.length; i++) {
                        selectionsDispatch({ type: "add", selection: data[i] })
                    }
                })
                .catch(() => {
                    setMessage({ text: "Error", type: "error" })
                })
        }
    }, []);

    if (selections.length == 0) {
        return (<tr role="noSelections">You haven't made any selections for this week</tr>)
    }

    return (
        <Table>
            <thead>
                <tr>
                    <th scope="col" role="tickerHeader">Ticker</th>
                    <th scope="col" role="sundayHeader">Sunday</th>
                </tr>
            </thead>
            <tbody>
                {selections.map((selection: ISelection) => (
                    <SelectionRow selection={selection} setMessage={setMessage} options={options} selectionsDispatch={selectionsDispatch} />
                ))}
            </tbody>
        </Table>
    );
}