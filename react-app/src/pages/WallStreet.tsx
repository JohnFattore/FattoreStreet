import OptionTable from '../components/OptionTable'
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
    const [selections, selectionsDispatch] = useReducer(selectionReducer, []);
    const [options, optionsDispatch] = useReducer(optionsReducer, []);
    const [currentSelections, currentSelectionsDispatch] = useReducer(selectionReducer, []);
    const [currentOptions, currentOptionsDispatch] = useReducer(optionsReducer, []);

    return (
        <>
            <h3>Options For This Week</h3>
            <OptionTable setMessage={setMessage} options={options} selections={selections} optionsDispatch={optionsDispatch} selectionsDispatch={selectionsDispatch} nextSunday={false}/>
            <h3>Your This Week Selections</h3>
            <SelectionTable selections={selections} selectionsDispatch={selectionsDispatch} setMessage={setMessage} options={options} nextSunday={false} />
            <h3>Options For Next Week</h3>
            <OptionTable setMessage={setMessage} options={currentOptions} selections={currentSelections} optionsDispatch={currentOptionsDispatch} selectionsDispatch={currentSelectionsDispatch} nextSunday={true}/>
            <h3>Your Next Week Selections</h3>
            <SelectionTable selections={currentSelections} selectionsDispatch={currentSelectionsDispatch} setMessage={setMessage} options={currentOptions} nextSunday={true} />
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
        </>
    );
}