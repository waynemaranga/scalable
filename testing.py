import os
from openai import AzureOpenAI
import pyodbc

# pip install pyodbc
# For Linux/macOS: https://learn.microsoft.com/en-us/sql/connect/odbc/linux-mac/installing-the-microsoft-odbc-driver-for-sql-server?view=sql-server-ver16&tabs=ubuntu18-install%2Cubuntu17-install%2Cdebian8-install%2Credhat7-13-install%2Crhel7-offline#18

AZURE_OPENAI_ENDPOINT = os.getenv("AZURE_OPENAI_ENDPOINT")
AZURE_OPENAI_API_KEY = os.getenv("AZURE_OPENAI_API_KEY")
AZURE_MSSQL_CONN_STR = os.getenv("AZURE_MSSQL_CONN_STR")

client = AzureOpenAI(
    api_version="2024-12-01-preview",
    azure_endpoint=AZURE_OPENAI_ENDPOINT,
    api_key=AZURE_OPENAI_API_KEY
    )


def create_chat_completion(prompt:str, model:str="o3-mini"):
    response = client.chat.completions.create(
        messages=[
            { "role": "system", "content": "You are a helpful assistant." },
            { "role": "user", "content": prompt }
        ],
        # max_completion_tokens=100000,
        model=model,
    )
    return response.choices[0].message.content

def connect_mssql(driver:str="ODBC Driver 18 for SQL Server"):
    try:
        conn_str = f"DRIVER={{{driver}}};{AZURE_MSSQL_CONN_STR}"
        conn = pyodbc.connect(conn_str)
        print("✅ Connected to MSSQL Server")
        return conn
    except Exception as e:
        print(f"❌ MSSQL connection error: {e}")
        return None


if __name__ == "__main__":
    # prompt = "Who are the Beastie Boys?"
    # print(create_chat_completion(prompt))
    conn = connect_mssql()
    if conn:
        cursor = conn.cursor()
        cursor.execute("SELECT CURRENT_TIMESTAMP")

    print("\n🐬")