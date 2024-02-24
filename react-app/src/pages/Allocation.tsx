import AllocationTable from "../components/AllocationTable";
import Alert from 'react-bootstrap/Alert';
import { useState } from "react";
import { IMessage } from "../interfaces";
import { setAlertVarient } from "../components/helperFunctions";

export default function Allocation() {
        const [message, setMessage] = useState<IMessage>({ text: "", type: "" })

        return (
                <>
                        <h1 role="pageHeader">User's Allocation of Assets</h1>
                        {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
                        <AllocationTable setMessage={setMessage} />
                </>

        );
}