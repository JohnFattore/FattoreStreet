import { useParams } from "react-router-dom";
import { useGetAccountQuery, useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import AssetTable from "../components/AssetTable";
import AssetForm from "../components/AssetForm";
import AccountSummary from "../components/AccountSummary";
import LoadingModal from "../components/LoadingModal";
import { Alert, Container } from "react-bootstrap";
import { useSelector } from "react-redux";
import { RootState } from "../main";

export default function AccountView() {
    const { id } = useParams();
    const accountId = Number(id);
    const { access } = useSelector((state: RootState) => state.user);

    // 1. Fetch Account Details
    const { data: account, error: accountError, isLoading: accountLoading } = useGetAccountQuery(accountId, {
        skip: !accountId
    });

    // 2. Fetch Assets for this account to get tickers
    const { data: assetsRaw, isLoading: assetsLoading, error: assetsError } = useGetAssetsQuery(accountId, {
        skip: !access || !accountId
    });
    const assets = assetsRaw ?? [];

    // 3. Fetch Asset Infos based on tickers
    const tickers = [...new Set(assets.map((a) => a.ticker))];
    const { isLoading: assetInfoLoading, error: assetInfoError } = useGetAssetInfosQuery(tickers, {
        skip: tickers.length === 0 || !access,
    });

    const isLoading = accountLoading || assetsLoading || assetInfoLoading;
    const error = accountError || assetsError || assetInfoError;

    if (error) {
        return (
            <Container className="mt-4">
                <Alert variant="danger">Error loading account data. Please try again later.</Alert>
            </Container>
        );
    }

    return (
        <Container className="mt-4">
            <LoadingModal show={isLoading} />

            {!isLoading && account && (
                <>
                    <AssetForm defaultAccountId={accountId} />
                    <h1>{account.name}</h1>
                    <h5 className="text-muted">{account.account_type.replace('_', ' ')}</h5>
                    <hr />
                    <AccountSummary accountId={accountId} />
                    <AssetTable accountId={accountId} />
                </>
            )}
        </Container>
    );
}
