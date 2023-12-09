import React from 'react';
import axios from 'axios';
import Table from 'react-bootstrap/Table';
import AssetRow from './AssetRow'
// import Django URL Context
import { useContext } from 'react';
import { strDjangoURLContext } from '../App';

function AssetTable() {
  // holds data from POST to server
  // not sure why the usage of useState is needed, but it doesnt work without it
  const [assets, setAssets] = React.useState([])

  const strDjangoURL = useContext(strDjangoURLContext);

  // post to server
  React.useEffect(() => {
    axios.get(strDjangoURL.concat("assets/"), {
      headers: {
        'Authorization': ' Bearer '.concat(sessionStorage.getItem("token") as string)
      },
    })
      .then((response) => {
        // get response data
        setAssets(response.data);
      })
  }, []);

  if (!assets) return null;

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
          <th scope="col">Account</th>
        </tr>
      </thead>
      <tbody>
      {assets.map(asset => (
        <AssetRow asset={asset} />
      ))}
      </tbody>
    </Table>
  );
}
export default AssetTable;