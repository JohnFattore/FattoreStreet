import OptionTable from '../components/OptionTable'
import OptionSelectionTable from '../components/OptionSelectionTable';
import { useState } from 'react';
import { IMessage } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';
import Alert from 'react-bootstrap/Alert';
import SelectionTable from '../components/SelectionTable';
import { useReducer } from 'react';

function selectionReducer(selections, action) {
    switch (action.type) {
        case 'add': {
            return [...selections, action.selection];
        }
        case 'delete': {
            return selections.filter(e => e !== action.selection)
        }
    }
}

function optionsReducer(options, action) {
    switch (action.type) {
        case 'add': {
            return [...options, action.option];
        }
        case 'delete': {
            return options.filter(e => e !== action.option)
        }
    }
}

export default function WallStreet() {
    const [message, setMessage] = useState<IMessage>({ text: "", type: "" })
    // this week / next week
    const [nextWeekSelections, nextWeekSelectionsDispatch] = useReducer(selectionReducer, []);
    const [nextWeekOptions, nextWeekOptionsDispatch] = useReducer(optionsReducer, []);
    const [lastWeekSelections, lastWeekSelectionsDispatch] = useReducer(selectionReducer, []);
    const [lastWeekOptions, lastWeekOptionsDispatch] = useReducer(optionsReducer, []);

    return (
        <>
            <h3>Current Stocks</h3>
            <OptionSelectionTable setMessage={setMessage} options={lastWeekOptions} selections={lastWeekSelections} optionsDispatch={lastWeekOptionsDispatch} selectionsDispatch={lastWeekSelectionsDispatch} week={0} />
            <h3>Options For Next Week</h3>
            <OptionTable setMessage={setMessage} options={nextWeekOptions} selections={nextWeekSelections} optionsDispatch={nextWeekOptionsDispatch} selectionsDispatch={nextWeekSelectionsDispatch} week={1}/>
            <h3>Your Next Week Selections</h3>
            <SelectionTable selections={nextWeekSelections} selectionsDispatch={nextWeekSelectionsDispatch} setMessage={setMessage} options={nextWeekOptions} week={1}/>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
        </>
    );
}