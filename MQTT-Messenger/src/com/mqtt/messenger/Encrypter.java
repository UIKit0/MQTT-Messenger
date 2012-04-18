package com.mqtt.messenger;

import android.util.Log;

public class Encrypter {
	
	static int phi,M,n,e,d,C,FLAG,p,q,s;
	static char[] output = new char[1000];
	
	
	public static String encrypt(String plain) {
		
			char[] temp = new char[2000];
			if(plain.length()%2==0)
				temp = plain.toCharArray();
			else
			{
				plain +="\u0000";
				temp = plain.toCharArray();
			}
			Log.d("Encrypter", plain + "<->" + temp.toString());
			p=29;
			q=31;
			n = p*q;
			phi=(p-1)*(q-1);
			e=23;
			d = 1;
			do
			{
				s = (d*e)%phi;
				d++;
			}while(s!=1);
			d = d-1;
			rsa(temp);
			String k = new String(output);
			k = k.substring(0, k.indexOf("~"));
			return k;
	}
	
	public static void encrypt()
	{
			int i;
			C = 1;
			for(i=0;i<e;i++)
				C=(C*M)%n;
			C =C%n;
	}
	
	public static void rsa(char[] str)
	{
		int len=str.length;
		int[] n = new int[3];
		int[] x = new int[3];
		int sum,i,j,k=0,index=0;
		if(len%2==1)
		{
			str[len]=0;
			str[len+1]='\0';
			len++;
		}
		for(i=0;i<len;i++)
		{
			sum=(int)str[i];
			sum*=1000;
			i++;
			sum+=(int)str[i];
			
			for(j=2;j>=0;j--)
			{
				n[j]=sum%100;
				sum/=100;
				M=n[j];
				encrypt();
				n[j]=C;
			}
			for(j=0;j<3;j++)
			{
				for(k=2;k>=0;k--)
				{
					x[k]=n[j]%10;
					n[j]/=10;
					//int aNumber = (int) (Math.random()*100000);
					//x[k]+=((1+aNumber%8)*10);
					x[k]+=32;
				}
				
				for(k=0;k<3;k++)
				{
					char temp = (char)x[k];
					output[index]=temp;
					switch(output[index])
					{
					case '`':
					case '/': 	output[index]='V';
							  	break;
					case '\'': output[index]=';';
								break;
					case '\"': output[index]='6';
								break;
					case '\\': output[index]='z';
							   break;
					}
					index++;
				}
			}
		}
		output[index]='~';	//delimiter
		
	}
}
