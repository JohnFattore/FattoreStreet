//import OptionTable from '../oldTables/OptionTable'
import OptionSelectionTable from '../components/OptionSelectionTable';
import { useState, useEffect } from 'react';
import { IMessage, IOption, ISelection } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';
import Alert from 'react-bootstrap/Alert';
//import SelectionTable from '../oldTables/SelectionTable';
import { useReducer } from 'react';
import DjangoTable from '../components/DjangoTable';
import { getOptions, getSelections/*, postSelection */} from '../components/axiosFunctions';
import { useQuote } from '../components/customHooks';

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

    let data: IOption[] = []
    useEffect(() => {
        if (nextWeekOptions.length == 0) {
            getOptions(1, 'false')
                .then((response) => {
                    data = response.data
                    for (let i = 0; i < data.length; i++) {
                        nextWeekOptionsDispatch({ type: "add", option: data[i] })
                    }
                })
                .catch(() => {
                    setMessage({ text: "Error", type: "error" })
                })
        }
    }, []);

    data = []
    useEffect(() => {
        if (lastWeekOptions.length == 0) {
            getOptions(0, 'false')
                .then((response) => {
                    data = response.data
                    for (let i = 0; i < data.length; i++) {
                        lastWeekOptionsDispatch({ type: "add", option: data[i] })
                    }
                })
                .catch(() => {
                    setMessage({ text: "Error", type: "error" })
                })
        }
    }, []);

    let dataSel: ISelection[] = []
    useEffect(() => {
        if (nextWeekSelections.length == 0) {
            getSelections(1)
                .then((response) => {
                    dataSel = response.data
                    for (let i = 0; i < dataSel.length; i++) {
                        nextWeekSelectionsDispatch({ type: "add", selection: dataSel[i] })
                    }
                })
                .catch(() => {
                    setMessage({ text: "Error", type: "error" })
                })
        }
    }, []);

    dataSel = []
    useEffect(() => {
        if (lastWeekOptions.length == 0) {
            getSelections(0)
                .then((response) => {
                    dataSel = response.data
                    for (let i = 0; i < dataSel.length; i++) {
                        lastWeekSelectionsDispatch({ type: "add", selection: dataSel[i] })
                    }
                })
                .catch(() => {
                    setMessage({ text: "Error", type: "error" })
                })
        }
    }, []);

    const fields = {
        ticker: {name: "Ticker"},
        name: {name: "Name"},
        sunday: {name: "Sunday of Week"},
        quote: {name: "Quote", function: useQuote, parameters: ['ticker', setMessage], item: "price", type: "money" },
    }

    const fieldsSel = {
        ticker: {name: "Ticker"},
        name: {name: "Name"},
        sunday: {name: "Sunday"},
    }
/*
    const axiosFunctions = {
        relatedModels: getSelections,
        post: postSelection
    }
*/
    return (
        <>
            <h3>Current Stocks</h3>
            <OptionSelectionTable setMessage={setMessage} options={lastWeekOptions} selections={lastWeekSelections} />
            <h3>Options For Next Week</h3>
            <DjangoTable setMessage={setMessage} models={nextWeekOptions} dispatch={nextWeekOptionsDispatch} fields={fields} />
            <h3>Your Next Week Selections</h3>
            <DjangoTable models={nextWeekSelections} dispatch={nextWeekSelectionsDispatch} setMessage={setMessage} fields={fieldsSel} />
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
        </>
    );
}