import React from 'react';
import Table from 'react-bootstrap/Table';
import AssetRow from './AssetRow'
import { getAssets } from './AxiosFunctions';

export default function AssetTable({change, setChange}) {
  const [assets, setAssets] = React.useState([])

  React.useEffect(() => {
    getAssets()
      .then((response) => {
        setAssets(response.data);
        setChange(false)
      }).catch(() => {
        alert("Error")
      })
  }, [change]);

  if (!assets) return null;

  if (assets.length == 0) {
    return (<h3>You don't own any assets</h3>)
  }

  return (
    <Table>
      <thead>
        <tr>
          <th scope="col">Ticker</th>
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
        {assets.map((asset, index)  => (
          <AssetRow asset={asset} setChange={setChange} index={index}/>
        ))}
      </tbody>
    </Table>
  );
}