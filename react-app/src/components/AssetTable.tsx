import React from 'react';
import axios from 'axios';
import Table from 'react-bootstrap/Table';
import AssetRow from './AssetRow'
// import Django URL Context
import { useContext } from 'react';
import { ENVContext } from './ENVContext';

function AssetTable({change, setChange}) {
  // holds data from POST to server
  // not sure why the usage of useState is needed, but it doesnt work without it
  const [assets, setAssets] = React.useState([])
  // assets is changed in useEffect, so another variable is needed to not cause the useEffect function to loop infinitly
  //const [change, setChange] = React.useState(false)

  const ENV = useContext(ENVContext);

  // post to server
  React.useEffect(() => {
    axios.get(ENV.djangoURL.concat("assets/"), {
      headers: {
        'Authorization': ' Bearer '.concat(sessionStorage.getItem("token") as string)
      },
    })
      .then((response) => {
        // get response data
        setAssets(response.data);
        setChange(false)
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
        {assets.map(asset => (
          <AssetRow asset={asset} setChange={setChange}/>
        ))}
      </tbody>
    </Table>
  );
}
export default AssetTable;