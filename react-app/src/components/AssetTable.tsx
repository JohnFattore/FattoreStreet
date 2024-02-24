import { useEffect } from 'react';
import Table from 'react-bootstrap/Table';
import AssetRow from './AssetRow'
import { IAsset } from '../interfaces';
import { getAssets } from './AxiosFunctions';

export default function AssetTable({ setMessage, assets, setAssets}) {

  useEffect(() => {
    getAssets()
      .then((response) => {
        const data: IAsset[] = response.data
        setAssets(data);
      }).catch(() => {
        setMessage({text: "Error: Can't get assets", type: "error"})
      })
  },[]);

  if (!assets) return null;

  if (assets.length == 0) {
    return (<h3 role="noAssets">You don't own any assets</h3>)
  }

  return (
    <Table>
      <thead>
        <tr>
          <th scope="col" role="tickerHeader">Ticker</th>
          <th scope="col">Shares</th>
          <th scope="col">Cost Basis</th>
          <th scope="col">Total Cost Basis</th>
          <th scope="col">Total Market Price</th>
          <th scope="col">Percent Change</th>
          <th scope="col">Buy Date</th>
          <th scope="col">Delete</th>
        </tr>
      </thead>
      <tbody>
        {assets.map((asset: IAsset) => (
          <AssetRow asset={asset} setMessage={setMessage} assets={assets} setAssets={setAssets}/>
        ))}
      </tbody>
    </Table>
  );
}