import { IMessage } from "../interfaces";
import { useState, useReducer } from "react";
import ResultTable from "../components/ResultTable";
import { setAlertVarient } from '../components/helperFunctions';
import Alert from 'react-bootstrap/Alert';
import DjangoTable from "../components/DjangoTable";

function resultReducer(results, action) {
    switch (action.type) {
        case 'add': {
            return [...results, action.result];
        }
        case 'delete': {
            return results.filter(e => e !== action.result)
        }
    }
}

function slamDunk(id: number) {
    return id + 5
}

export default function Results() {
    const [message, setMessage] = useState<IMessage>({ text: "", type: "" })
    const [results, resultsDispatch] = useReducer(resultReducer, []);

    let newField = {field: "MAXWELL", function: slamDunk, parameters: ["id"] }
    let extraFields = [newField]

    return (
        <>
            <h3>Wallstreet Weekly Results</h3>
            <ResultTable setMessage={setMessage} results={results} resultsDispatch={resultsDispatch}/>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
            <DjangoTable models={results} setMessage={setMessage} dispatch={resultsDispatch} extraFields={extraFields} excludeFields={[]}/>
        </>

    );
}