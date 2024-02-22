import AllocationTable from "../components/AllocationTable";
import Alert from 'react-bootstrap/Alert';
import { useState } from "react";

export default function Allocation() {
        const [error, setError] = useState("noError")

        return (
                <>
                        <h1 role="pageHeader">User's Allocation of Assets</h1>
                        {error != "noError" && <Alert variant='danger' dismissible>{error}</Alert>}
                        <AllocationTable setError={setError}/>
                </>

        );
}