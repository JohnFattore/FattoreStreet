import { useParams } from "react-router-dom";
import { useGetAccountQuery } from "../functions/api";
import AssetTable from "../components/AssetTable";
import AssetForm from "../components/AssetForm";
import { Alert, Container } from "react-bootstrap";

export default function AccountView() {
    const { id } = useParams();
    const accountId = Number(id);
    const { data: account, error, isLoading } = useGetAccountQuery(accountId, {
        skip: !accountId
    });

    if (isLoading) return <Container className="mt-4"><p>Loading account details...</p></Container>;
    if (error || !account) return <Container className="mt-4"><Alert variant="danger">Error loading account</Alert></Container>;

    return (
        <Container className="mt-4">
            <AssetForm defaultAccountId={accountId} />
            <h1>{account.name}</h1>
            <h5 className="text-muted">{account.account_type.replace('_', ' ')}</h5>
            <hr />
            <AssetTable accountId={accountId} />
        </Container>
    );
}
