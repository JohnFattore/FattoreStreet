import { Button, Table } from "react-bootstrap";
import { useGetAccountsQuery, useGetAssetsQuery, useGetAssetInfosQuery } from "../functions/api";
import { useNavigate } from "react-router-dom";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { formatString } from "../functions/helperFunctions";
import StateHandler from "./StateHandler";

export default function AccountList() {
    const navigate = useNavigate();
    const { access, username } = useSelector((state: RootState) => state.user);
    const { data: accounts, error: accountsError, isLoading: accountsLoading } = useGetAccountsQuery(undefined, {
        skip: !access
    });

    // Fetch all assets to calculate balances
    const { data: assets, isLoading: assetsLoading } = useGetAssetsQuery(undefined, {
        skip: !access
    });

    const uniqueTickers = assets ? [...new Set(assets.map(a => a.ticker))] : [];

    const { data: assetInfos, isLoading: assetInfosLoading } = useGetAssetInfosQuery(uniqueTickers, {
        skip: !access || uniqueTickers.length === 0
    });

    if (!access) return null;

    return (
    <StateHandler
      isLoading={accountsLoading}
      errors={[accountsError]}
      content={
        <div className="account-list">
            <h3>{username}'s Accounts</h3>
            {accounts && accounts.length > 0 ? (
                <Table striped bordered hover>
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Type</th>
                            <th>Balance</th>
                            <th>Day Change</th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        {accounts.map((account) => {
                            let balance = 0;
                            let dayChange = 0;
                            let previousBalance = 0;

                            if (assets && assetInfos) {
                                const accountAssets = assets.filter((a: any) => a.account === account.id); // 'account' is the field name from django

                                accountAssets.forEach((asset: any) => {
                                    const info = assetInfos[asset.ticker];
                                    if (info && !asset.sellDate) {
                                        const currentVal = asset.shares * info.currentPrice;
                                        balance += currentVal;

                                        // Day change calculation
                                        // Current Value - (Current Value / (1 + percentChangeDaily)) = Dollar Change
                                        // Or: Previous Close = Current Price / (1 + percentChangeDaily)
                                        // Dollar Change = (Current Price - Previous Close) * Shares

                                        const previousPrice = info.currentPrice / (1 + info.percentChangeDaily);
                                        const previousVal = asset.shares * previousPrice;
                                        previousBalance += previousVal;

                                        dayChange += (currentVal - previousVal);
                                    }
                                });
                            }

                            const dayChangePercent = previousBalance > 0 ? dayChange / previousBalance : 0;
                            const isDataLoading = assetsLoading || assetInfosLoading;

                            return (
                                <tr key={account.id}>
                                    <td>{account.name}</td>
                                    <td>{account.account_type.replace('_', ' ')}</td>
                                    <td>{isDataLoading ? "Loading..." : formatString(balance, "money")}</td>
                                    <td className={dayChange >= 0 ? "text-success" : "text-danger"}>
                                        {isDataLoading ? "Loading..." : (
                                            <>
                                                {formatString(dayChange, "money")} ({formatString(dayChangePercent, "percent")})
                                            </>
                                        )}
                                    </td>
                                    <td>
                                        <Button
                                            variant="primary"
                                            size="sm"
                                            onClick={() => navigate(`/account/${account.id}`)}
                                        >
                                            View Details
                                        </Button>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </Table>
            ) : (
                <p>No accounts found.</p>
            )}
        </div>
      }
    />
    );
}
