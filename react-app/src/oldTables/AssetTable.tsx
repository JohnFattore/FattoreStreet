import Table from 'react-bootstrap/Table';
import AssetRow from './AssetRow'
import { IAsset } from '../interfaces';
import { useEffect } from 'react';
import { getAssets } from '../components/axiosFunctions';

export default function AssetTable({ setMessage, assets, dispatch }) {

  let data: IAsset[] = []
  useEffect(() => {
    if (assets.length == 0) {
      getAssets()
        .then((response) => {
          data = response.data
          for (let i = 0; i < data.length; i++) {
            dispatch({ type: "add", asset: data[i] })
          }
        }).catch(() => {
          setMessage({ text: "There was a problem getting assets", type: "error" })
        }) 
    }
  }, []);
 
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
          <AssetRow asset={asset} setMessage={setMessage} dispatch={dispatch} key={asset.id}/>
        ))}
      </tbody>
    </Table>
  );
}